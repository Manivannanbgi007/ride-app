package com.fooddelivery.chatservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.fooddelivery.chatservice.client.PresenceLookup;
import com.fooddelivery.chatservice.client.UserInfo;
import com.fooddelivery.chatservice.client.UserLookup;
import com.fooddelivery.chatservice.entity.Message;
import com.fooddelivery.chatservice.entity.OutboxEvent;
import com.fooddelivery.chatservice.exception.DependencyUnavailableException;
import com.fooddelivery.chatservice.repository.MessageRepository;
import com.fooddelivery.chatservice.repository.OutboxEventRepository;
import com.fooddelivery.events.AvroBinary;
import com.jayway.jsonpath.JsonPath;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real MySQL and Kafka (Testcontainers). user-service and notification-service are
 * replaced by mocks (@MockitoBean). The outbox relay runs, so the tests also see the events reach Kafka.
 * <p>
 * Tests share one database, so each test uses its own user ids (and a name prefix where it lists the inbox).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ChatServiceIntegrationTest {

    private static final long ADMIN = 1L;
    private static final long OTHER_ADMIN = 2L;
    /** Shared counter: every id is unique across the tests of this class. */
    private static final AtomicLong IDS = new AtomicLong(10_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @MockitoBean
    private UserLookup userLookup;
    @MockitoBean
    private PresenceLookup presenceLookup;

    @BeforeEach
    void setUp() {
        // Nobody is known and nobody is online unless a test says so
        when(userLookup.findUser(anyLong())).thenReturn(Optional.empty());
        when(presenceLookup.findOnline(any())).thenReturn(Optional.of(Set.of()));
    }

    // ------------------------------------------------------------------ the whole conversation

    @Test
    void aSupportConversationFromTheFirstQuestionToTheAnswerRead() throws Exception {
        long user = newUser("Asha Rao", "CUSTOMER");
        when(userLookup.findUser(ADMIN)).thenReturn(Optional.of(new UserInfo(ADMIN, "Ravi Kumar", "PLATFORM_ADMIN")));
        when(presenceLookup.findOnline(any())).thenReturn(Optional.of(Set.of(user)));
        long unreadBefore = staffUnreadCount();

        // The participant writes: the conversation is created, the staff has one unread
        ResultActions first = participantSends(user, "CUSTOMER", "Where is my order?", UUID.randomUUID().toString());
        first.andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Where is my order?"))
                .andExpect(jsonPath("$.senderName").value("Asha Rao"))
                .andExpect(jsonPath("$.senderRole").value("CUSTOMER"))
                .andExpect(jsonPath("$.fromStaff").value(false))
                .andExpect(jsonPath("$.readAt").value(nullValue()));
        long conversationId = ((Number) JsonPath.read(content(first), "$.conversationId")).longValue();
        long firstMessageId = ((Number) JsonPath.read(content(first), "$.id")).longValue();

        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.lastMessage.preview").value("Where is my order?"))
                .andExpect(jsonPath("$.lastMessage.fromStaff").value(false))
                .andExpect(jsonPath("$.lastMessage.read").value(false))
                .andExpect(jsonPath("$.participant.online").value(nullValue()));

        // The admin finds it in the inbox with one unread, and sees that the user is online
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations").param("search", "asha rao"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(conversationId))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1))
                .andExpect(jsonPath("$.content[0].participant.name").value("Asha Rao"))
                .andExpect(jsonPath("$.content[0].participant.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.content[0].participant.online").value(true))
                .andExpect(jsonPath("$.content[0].pinned").value(false));
        assertThat(staffUnreadCount()).isEqualTo(unreadBefore + 1);

        // The admin reads it: the count drops for every admin, and the participant sees the message as read
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/read", conversationId), OTHER_ADMIN))
                .andExpect(status().isNoContent());
        assertThat(staffUnreadCount()).isEqualTo(unreadBefore);
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", conversationId), ADMIN))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.lastMessage.read").value(true));
        mockMvc.perform(participant(get("/api/v1/chat/messages"), user, "CUSTOMER"))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].readAt").isNotEmpty());

        // The admin answers, signed with his own name: the participant has one unread
        ResultActions reply = mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/messages",
                conversationId), ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(messageJson("It left the kitchen 5 minutes ago.", UUID.randomUUID().toString())));
        reply.andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromStaff").value(true))
                .andExpect(jsonPath("$.senderName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.senderRole").value("PLATFORM_ADMIN"));
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "CUSTOMER"))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.lastMessage.fromStaff").value(true))
                .andExpect(jsonPath("$.lastMessage.read").value(false));
        // Nothing new for the staff: only the participant's side has something unread
        assertThat(staffUnreadCount()).isEqualTo(unreadBefore);

        // The participant reads it
        mockMvc.perform(participant(post("/api/v1/chat/read"), user, "CUSTOMER")).andExpect(status().isNoContent());
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "CUSTOMER"))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.lastMessage.read").value(true));

        // Events: sent, read by staff, sent, read by the participant; ids only, up to the right message
        List<OutboxEvent> events = events(conversationId);
        assertThat(events).extracting(OutboxEvent::eventName)
                .containsExactly("ChatMessageSent", "ChatMessagesRead", "ChatMessageSent", "ChatMessagesRead");
        assertThat(events).extracting(OutboxEvent::getTopic).containsExactly("fooddelivery.chat.message-sent",
                "fooddelivery.chat.messages-read", "fooddelivery.chat.message-sent", "fooddelivery.chat.messages-read");
        assertThat(events).extracting(OutboxEvent::getMessageKey).containsOnly(String.valueOf(conversationId));
        GenericRecord sentByUser = decode(events.get(0));
        assertThat(sentByUser.get("messageId")).isEqualTo(firstMessageId);
        assertThat(sentByUser.get("participantUserId")).isEqualTo(user);
        assertThat(sentByUser.get("senderUserId")).isEqualTo(user);
        assertThat(sentByUser.get("fromStaff")).isEqualTo(false);
        GenericRecord readByStaff = decode(events.get(1));
        assertThat(readByStaff.get("readByStaff")).isEqualTo(true);
        assertThat(readByStaff.get("upToMessageId")).isEqualTo(firstMessageId);
        GenericRecord sentByStaff = decode(events.get(2));
        assertThat(sentByStaff.get("senderUserId")).isEqualTo(ADMIN);
        assertThat(sentByStaff.get("fromStaff")).isEqualTo(true);
        GenericRecord readByParticipant = decode(events.get(3));
        assertThat(readByParticipant.get("readByStaff")).isEqualTo(false);
        assertThat(readByParticipant.get("upToMessageId")).isEqualTo(sentByStaff.get("messageId"));

        // The relay delivers them to Kafka
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(events(conversationId)).allSatisfy(event -> assertThat(event.getPublishedAt()).isNotNull()));
    }

    // ------------------------------------------------------------------ retries and limits

    @Test
    void sendingTheSameClientMessageIdAgainNeverDuplicates() throws Exception {
        long user = newUser("Retry Rao", "RESTAURANT_ADMIN");
        String clientId = UUID.randomUUID().toString();

        ResultActions first = participantSends(user, "RESTAURANT_ADMIN", "Hello", clientId);
        first.andExpect(status().isCreated());
        long conversationId = ((Number) JsonPath.read(content(first), "$.conversationId")).longValue();
        long messageId = ((Number) JsonPath.read(content(first), "$.id")).longValue();

        // The retry of a lost response (and the same id in capitals) returns the saved message with 200
        participantSends(user, "RESTAURANT_ADMIN", "Hello", clientId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(messageId));
        participantSends(user, "RESTAURANT_ADMIN", "Hello", clientId.toUpperCase(java.util.Locale.ROOT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(messageId));

        mockMvc.perform(participant(get("/api/v1/chat/messages"), user, "RESTAURANT_ADMIN"))
                .andExpect(jsonPath("$.messages", hasSize(1)));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", conversationId), ADMIN))
                .andExpect(jsonPath("$.unreadCount").value(1));
        assertThat(events(conversationId)).extracting(OutboxEvent::eventName).containsExactly("ChatMessageSent");

        // The same for a reply of the staff
        String replyId = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/messages", conversationId), ADMIN)
                            .contentType(MediaType.APPLICATION_JSON).content(messageJson("On it", replyId)))
                    .andExpect(status().is(i == 0 ? 201 : 200));
        }
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "RESTAURANT_ADMIN"))
                .andExpect(jsonPath("$.unreadCount").value(1));
        assertThat(events(conversationId)).hasSize(2);
    }

    @Test
    void simultaneousRetriesOfTheFirstMessageCreateOneConversationAndOneMessage() throws Exception {
        long user = newUser("Racer Rao", "DELIVERY_PARTNER");
        String clientId = UUID.randomUUID().toString();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> call = () -> {
                start.await();
                return mockMvc.perform(participant(post("/api/v1/chat/messages"), user, "DELIVERY_PARTNER")
                                .contentType(MediaType.APPLICATION_JSON).content(messageJson("Hi", clientId)))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnly(200, 201);
        assertThat(statuses.stream().filter(code -> code == 201)).hasSize(1);
        mockMvc.perform(participant(get("/api/v1/chat/messages"), user, "DELIVERY_PARTNER"))
                .andExpect(jsonPath("$.messages", hasSize(1)));
        long conversationId = conversationIdOf(user, "DELIVERY_PARTNER");
        assertThat(events(conversationId)).hasSize(1);
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", conversationId), ADMIN))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void aSenderAboveThirtyMessagesAMinuteGets429AndAnEarlierMessageCanStillBeRetried() throws Exception {
        long user = newUser("Chatty Rao", "CUSTOMER");
        String firstId = UUID.randomUUID().toString();
        participantSends(user, "CUSTOMER", "message 1", firstId).andExpect(status().isCreated());
        for (int i = 2; i <= 30; i++) {
            participantSends(user, "CUSTOMER", "message " + i, UUID.randomUUID().toString())
                    .andExpect(status().isCreated());
        }

        participantSends(user, "CUSTOMER", "message 31", UUID.randomUUID().toString())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too many messages"));
        // A retry of a message that was saved is not "a new message"
        participantSends(user, "CUSTOMER", "message 1", firstId).andExpect(status().isOk());

        mockMvc.perform(participant(get("/api/v1/chat/messages").param("limit", "100"), user, "CUSTOMER"))
                .andExpect(jsonPath("$.messages", hasSize(30)));
    }

    // ------------------------------------------------------------------ reading history

    @Test
    void seventyMessagesPageBackwardsWithoutDuplicatesOrGaps() throws Exception {
        long user = newUser("History Rao", "CUSTOMER");
        long conversationId = conversationIdOf(participantSendsOk(user, "CUSTOMER", "message 0"));
        // Written straight to the table: the API's rate limit would stop 70 messages in a row
        List<Long> expected = new ArrayList<>(List.of(firstMessageId(conversationId)));
        for (int i = 1; i < 70; i++) {
            String body = "message " + i;
            expected.add(messageRepository.save(new Message(conversationId, UUID.randomUUID().toString(), user,
                    "History Rao", "CUSTOMER", false, body, java.time.Instant.now())).getId());
        }

        // Participant API and staff API page the same way
        for (boolean staff : new boolean[] {false, true}) {
            List<Long> seen = new ArrayList<>();
            Long beforeId = null;
            int pages = 0;
            boolean hasMore = true;
            while (hasMore) {
                MockHttpServletRequestBuilder request = staff
                        ? admin(get("/api/v1/admin/chat/conversations/{id}/messages", conversationId), ADMIN)
                        : participant(get("/api/v1/chat/messages"), user, "CUSTOMER");
                request.param("limit", "30");
                if (beforeId != null) {
                    request.param("beforeId", String.valueOf(beforeId));
                }
                String page = content(mockMvc.perform(request).andExpect(status().isOk()));
                List<Integer> ids = JsonPath.read(page, "$.messages[*].id");
                // Each page is oldest first; pages arrive newest first
                seen.addAll(0, ids.stream().map(Integer::longValue).toList());
                assertThat(ids).isSorted();
                hasMore = JsonPath.read(page, "$.hasMore");
                Object next = JsonPath.read(page, "$.nextBeforeId");
                assertThat(next == null).isEqualTo(!hasMore);
                beforeId = next == null ? null : ((Number) next).longValue();
                pages++;
            }
            assertThat(pages).isEqualTo(3);
            assertThat(seen).isEqualTo(expected);
        }
    }

    @Test
    void searchingAConversationIsCaseInsensitiveAndStillPaged() throws Exception {
        long user = newUser("Search Rao", "CUSTOMER");
        long conversationId = conversationIdOf(participantSendsOk(user, "CUSTOMER", "first message"));
        for (int i = 1; i <= 7; i++) {
            messageRepository.save(new Message(conversationId, UUID.randomUUID().toString(), user, "Search Rao",
                    "CUSTOMER", false, i % 2 == 0 ? "Refund number " + i : "plain " + i, java.time.Instant.now()));
        }
        messageRepository.save(new Message(conversationId, UUID.randomUUID().toString(), user, "Search Rao",
                "CUSTOMER", false, "I want 100% back", java.time.Instant.now()));

        // "refund" matches the messages 2, 4 and 6 whatever the case; two per page
        String firstPage = content(mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}/messages",
                conversationId).param("q", "REFUND").param("limit", "2"), ADMIN))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.messages[1].body").value("Refund number 6")));
        long next = ((Number) JsonPath.read(firstPage, "$.nextBeforeId")).longValue();
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}/messages", conversationId)
                        .param("q", "refund").param("limit", "2").param("beforeId", String.valueOf(next)), ADMIN))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].body").value("Refund number 2"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId").value(nullValue()));

        // A '%' in the search is a '%', not "anything"
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}/messages", conversationId)
                        .param("q", "100%"), ADMIN))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].body").value("I want 100% back"));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}/messages", conversationId)
                        .param("q", "nothing like this"), ADMIN))
                .andExpect(jsonPath("$.messages", hasSize(0)));
    }

    // ------------------------------------------------------------------ privacy

    @Test
    void twoParticipantsNeverSeeOrChangeEachOthersConversation() throws Exception {
        long asha = newUser("Asha Private", "CUSTOMER");
        long ben = newUser("Ben Private", "CUSTOMER");
        long ashaConversation = conversationIdOf(participantSendsOk(asha, "CUSTOMER", "my secret question"));
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/messages", ashaConversation), ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(messageJson("a private answer", UUID.randomUUID().toString())))
                .andExpect(status().isCreated());

        // Ben has nothing: no conversation, no messages, and reading changes nothing of Asha's
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), ben, "CUSTOMER")).andExpect(status().isNotFound());
        mockMvc.perform(participant(get("/api/v1/chat/messages"), ben, "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(0)))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId").value(nullValue()));
        mockMvc.perform(participant(post("/api/v1/chat/read"), ben, "CUSTOMER")).andExpect(status().isNoContent());
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), asha, "CUSTOMER"))
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Ben writes: it lands in his own conversation, and Asha's stays as it was
        long benConversation = conversationIdOf(participantSendsOk(ben, "CUSTOMER", "my question"));
        assertThat(benConversation).isNotEqualTo(ashaConversation);
        mockMvc.perform(participant(get("/api/v1/chat/messages"), ben, "CUSTOMER"))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].body").value("my question"));
        mockMvc.perform(participant(get("/api/v1/chat/messages"), asha, "CUSTOMER"))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].body").value("my secret question"))
                .andExpect(jsonPath("$.messages[1].body").value("a private answer"));
    }

    // ------------------------------------------------------------------ events only when something changed

    @Test
    void readingWithNothingToReadWritesNoEvent() throws Exception {
        long user = newUser("Quiet Rao", "CUSTOMER");
        long conversationId = conversationIdOf(participantSendsOk(user, "CUSTOMER", "hello"));

        // The participant has nothing unread; the staff reads twice
        mockMvc.perform(participant(post("/api/v1/chat/read"), user, "CUSTOMER")).andExpect(status().isNoContent());
        assertThat(events(conversationId)).extracting(OutboxEvent::eventName).containsExactly("ChatMessageSent");

        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/read", conversationId), ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/read", conversationId), OTHER_ADMIN))
                .andExpect(status().isNoContent());
        assertThat(events(conversationId)).extracting(OutboxEvent::eventName)
                .containsExactly("ChatMessageSent", "ChatMessagesRead");

        // A user who never wrote can "read" as well: nothing happens
        mockMvc.perform(participant(post("/api/v1/chat/read"), newUser("Never Wrote", "CUSTOMER"), "CUSTOMER"))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ the inbox

    @Test
    void theInboxPutsMyPinnedFirstThenTheLatestActivityAndPinAndMuteArePerAdmin() throws Exception {
        String tag = "Inbox" + IDS.incrementAndGet();
        long older = newUser(tag + " Older", "CUSTOMER");
        long newer = newUser(tag + " Newer", "DELIVERY_PARTNER");
        long silent = newUser(tag + " Silent", "RESTAURANT_ADMIN");
        long olderConversation = conversationIdOf(participantSendsOk(older, "CUSTOMER", "needle in the first message"));
        long newerConversation = conversationIdOf(participantSendsOk(newer, "DELIVERY_PARTNER", "second one"));
        // An admin starts a conversation with a user who never wrote: it has no message and goes last
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations"), ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + silent + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastMessage").value(nullValue()))
                .andExpect(jsonPath("$.unreadCount").value(0));

        assertThat(inboxNames(ADMIN, tag)).containsExactly(tag + " Newer", tag + " Older", tag + " Silent");

        // Pin is mine: the other admin still sees the plain order
        mockMvc.perform(admin(put("/api/v1/admin/chat/conversations/{id}/pin", olderConversation), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.muted").value(false));
        assertThat(inboxNames(ADMIN, tag)).containsExactly(tag + " Older", tag + " Newer", tag + " Silent");
        assertThat(inboxNames(OTHER_ADMIN, tag)).containsExactly(tag + " Newer", tag + " Older", tag + " Silent");

        // Mute is mine as well, and independent of pin
        mockMvc.perform(admin(put("/api/v1/admin/chat/conversations/{id}/mute", newerConversation), OTHER_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"muted\": true}"))
                .andExpect(jsonPath("$.muted").value(true))
                .andExpect(jsonPath("$.pinned").value(false));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", newerConversation), OTHER_ADMIN))
                .andExpect(jsonPath("$.muted").value(true));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", newerConversation), ADMIN))
                .andExpect(jsonPath("$.muted").value(false));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", olderConversation), OTHER_ADMIN))
                .andExpect(jsonPath("$.pinned").value(false));

        // Unpinning restores the plain order
        mockMvc.perform(admin(put("/api/v1/admin/chat/conversations/{id}/pin", olderConversation), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\": false}"))
                .andExpect(jsonPath("$.pinned").value(false));
        assertThat(inboxNames(ADMIN, tag)).containsExactly(tag + " Newer", tag + " Older", tag + " Silent");

        // Search matches the last message too, and paging keeps the order
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations").param("search", "NEEDLE"), ADMIN))
                .andExpect(jsonPath("$.content[*].id", contains((int) olderConversation)));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations").param("search", tag).param("size", "2")
                        .param("page", "1"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].participant.name").value(tag + " Silent"));
    }

    @Test
    void anAdminStartsAConversationAndTheParticipantFindsItWaiting() throws Exception {
        long user = newUser("Invited Rao", "CUSTOMER");
        long adminUser = IDS.incrementAndGet();
        when(userLookup.findUser(adminUser)).thenReturn(Optional.of(new UserInfo(adminUser, "Other Admin", "PLATFORM_ADMIN")));
        long unknown = IDS.incrementAndGet();

        String created = content(mockMvc.perform(admin(post("/api/v1/admin/chat/conversations"), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\": " + user + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participant.userId").value(user))
                .andExpect(jsonPath("$.participant.name").value("Invited Rao")));
        long conversationId = ((Number) JsonPath.read(created, "$.id")).longValue();
        // The same again is the same conversation, with 200
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations"), OTHER_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\": " + user + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId));

        // An unknown user is 404, an admin cannot be a participant (422)
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations"), ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + unknown + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations"), ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + adminUser + "}"))
                .andExpect(status().isUnprocessableContent());

        // The participant has a conversation without messages; then the admin writes first
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastMessage").value(nullValue()))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/messages", conversationId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(messageJson("Welcome!", UUID.randomUUID().toString())))
                .andExpect(status().isCreated())
                // user-service knows nobody here, so the admin signs as Support
                .andExpect(jsonPath("$.senderName").value("Support"));
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "CUSTOMER"))
                .andExpect(jsonPath("$.unreadCount").value(1));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", conversationId), ADMIN))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ------------------------------------------------------------------ other services failing

    @Test
    void presenceFailingMeansUnknownNotAnError() throws Exception {
        long user = newUser("Unknown Presence", "CUSTOMER");
        long conversationId = conversationIdOf(participantSendsOk(user, "CUSTOMER", "hi"));
        when(presenceLookup.findOnline(any())).thenReturn(Optional.empty());

        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations").param("search", "Unknown Presence"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].participant.online").value(nullValue()));
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", conversationId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participant.online").value(nullValue()));
    }

    @Test
    void whenUserServiceIsDownTheFirstMessageIsRefusedAndNothingIsSaved() throws Exception {
        long user = IDS.incrementAndGet();
        when(userLookup.findUser(user)).thenThrow(new DependencyUnavailableException("user-service",
                new IllegalStateException("connection refused")));

        participantSends(user, "CUSTOMER", "hello?", UUID.randomUUID().toString())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service unavailable"));

        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "CUSTOMER")).andExpect(status().isNotFound());

        // Once it is back, the same message goes through
        doReturn(Optional.of(new UserInfo(user, "Back Again", "CUSTOMER"))).when(userLookup).findUser(user);
        participantSends(user, "CUSTOMER", "hello?", UUID.randomUUID().toString()).andExpect(status().isCreated());
    }

    @Test
    void anAdminIdentifiedAsAParticipantByUserServiceCannotOpenAConversation() throws Exception {
        long user = IDS.incrementAndGet();
        when(userLookup.findUser(user)).thenReturn(Optional.of(new UserInfo(user, "Sneaky Admin", "PLATFORM_ADMIN")));

        participantSends(user, "CUSTOMER", "let me in", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(participant(get("/api/v1/chat/conversation"), user, "CUSTOMER")).andExpect(status().isNotFound());
    }

    @Test
    void unknownConversationsAreNotFoundForTheAdminEndpoints() throws Exception {
        long missing = 987_654_321L;
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}", missing), ADMIN))
                .andExpect(status().isNotFound());
        mockMvc.perform(admin(get("/api/v1/admin/chat/conversations/{id}/messages", missing), ADMIN))
                .andExpect(status().isNotFound());
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/messages", missing), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(messageJson("hi", UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        mockMvc.perform(admin(post("/api/v1/admin/chat/conversations/{id}/read", missing), ADMIN))
                .andExpect(status().isNotFound());
        mockMvc.perform(admin(put("/api/v1/admin/chat/conversations/{id}/pin", missing), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\": true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(admin(put("/api/v1/admin/chat/conversations/{id}/mute", missing), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"muted\": true}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    /** A new participant id; user-service "knows" it under the given name and role. */
    private long newUser(String name, String role) {
        long id = IDS.incrementAndGet();
        when(userLookup.findUser(id)).thenReturn(Optional.of(new UserInfo(id, name, role)));
        return id;
    }

    private static MockHttpServletRequestBuilder participant(MockHttpServletRequestBuilder request, long userId,
                                                             String role) {
        return request.with(TestTokens.as(userId, role));
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, long adminId) {
        return request.with(TestTokens.as(adminId, "PLATFORM_ADMIN"));
    }

    private static String messageJson(String body, String clientMessageId) {
        return "{\"body\": \"" + body + "\", \"clientMessageId\": \"" + clientMessageId + "\"}";
    }

    private ResultActions participantSends(long userId, String role, String body, String clientMessageId)
            throws Exception {
        return mockMvc.perform(participant(post("/api/v1/chat/messages"), userId, role)
                .contentType(MediaType.APPLICATION_JSON).content(messageJson(body, clientMessageId)));
    }

    /** Sends a message that must be accepted and returns the response body. */
    private String participantSendsOk(long userId, String role, String body) throws Exception {
        return content(participantSends(userId, role, body, UUID.randomUUID().toString()).andExpect(status().isCreated()));
    }

    private static long conversationIdOf(String messageResponse) {
        return ((Number) JsonPath.read(messageResponse, "$.conversationId")).longValue();
    }

    private long conversationIdOf(long userId, String role) throws Exception {
        return ((Number) JsonPath.read(content(mockMvc.perform(participant(get("/api/v1/chat/conversation"), userId, role))),
                "$.id")).longValue();
    }

    private long firstMessageId(long conversationId) {
        return messageRepository
                .findPage(conversationId, Long.MAX_VALUE, "%", org.springframework.data.domain.PageRequest.of(0, 1))
                .getFirst().getId();
    }

    private static String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private long staffUnreadCount() throws Exception {
        return ((Number) JsonPath.read(content(mockMvc.perform(admin(get("/api/v1/admin/chat/unread-count"), ADMIN))
                .andExpect(status().isOk())), "$.count")).longValue();
    }

    private List<String> inboxNames(long adminId, String search) throws Exception {
        return JsonPath.read(content(mockMvc.perform(admin(get("/api/v1/admin/chat/conversations")
                .param("search", search).param("size", "50"), adminId))
                .andExpect(status().isOk())), "$.content[*].participant.name");
    }

    private List<OutboxEvent> events(long conversationId) {
        return outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("CONVERSATION", conversationId);
    }

    private static GenericRecord decode(OutboxEvent event) {
        return AvroBinary.decode(event.getPayload(), new Schema.Parser().parse(event.getAvroSchema()));
    }
}
