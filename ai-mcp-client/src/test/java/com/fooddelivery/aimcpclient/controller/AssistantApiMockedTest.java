package com.fooddelivery.aimcpclient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.fooddelivery.aimcpclient.TestJwts;
import com.fooddelivery.aimcpclient.TestcontainersConfiguration;
import com.fooddelivery.aimcpclient.client.AgentServerClient;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatRequest;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatResponse;
import com.fooddelivery.aimcpclient.dto.response.ActionConfirmResponse;
import com.fooddelivery.aimcpclient.dto.response.StatusResponse;
import com.fooddelivery.aimcpclient.exception.AgentCallException;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import com.fooddelivery.aimcpclient.identity.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole public API with a real Redis (Testcontainers), a MOCKED ai-mcp-server (a Mockito mock) and real access
 * tokens (TestJwts, checked as in production): who may call, chat flow, conversations, limits, actions, error mapping,
 * suggestions, status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AssistantApiMockedTest {

    /** Every test gets its own user ids: conversations and limits are per user. */
    private static final AtomicLong NEXT_USER_ID = new AtomicLong(1000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private StringRedisTemplate redis;

    @MockitoBean
    private AgentServerClient agentServer;

    private long customerId;
    private long otherCustomerId;

    @DynamicPropertySource
    static void tokensOfTheTests(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwts::jwksUri);
    }

    @BeforeEach
    void setUp() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        reset(agentServer);
        customerId = NEXT_USER_ID.incrementAndGet();
        otherCustomerId = NEXT_USER_ID.incrementAndGet();
        when(agentServer.chat(any(), any())).thenReturn(reply("Hello!", "[]", null));
    }

    // ---- chat flow

    @Test
    void aNewConversationIsCreatedStoredAndRestored() throws Exception {
        when(agentServer.chat(any(), any())).thenReturn(reply("Here is a biryani.",
                "[{\"type\":\"food\",\"name\":\"Biryani\"}]", "{\"actionId\":\"act-1\",\"total\":250}"));

        String body = chat(customerId, null, "  I want a biryani  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.text").value("Here is a biryani."))
                .andExpect(jsonPath("$.reply.cards[0].name").value("Biryani"))
                .andExpect(jsonPath("$.reply.pendingAction.actionId").value("act-1"))
                .andExpect(jsonPath("$.reply.toolsUsed[0]").value("search_foods"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messageId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String conversationId = JsonPath.read(body, "$.conversationId");

        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].text").value("I want a biryani"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].cards[0].name").value("Biryani"))
                .andExpect(jsonPath("$.messages[1].pendingAction.actionId").value("act-1"))
                .andExpect(jsonPath("$.messages[1].at").isNotEmpty());
    }

    @Test
    void thePreviousTurnsAreSentToTheServerAsTextOnly() throws Exception {
        String conversationId = newConversation("first question");

        chat(customerId, conversationId, "second question").andExpect(status().isOk());

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentServer, times(2)).chat(caller(customerId, Role.CUSTOMER), captor.capture());
        AgentChatRequest second = captor.getAllValues().get(1);
        assertThat(second.message()).isEqualTo("second question");
        assertThat(second.history()).extracting(AgentChatRequest.HistoryEntry::role, AgentChatRequest.HistoryEntry::text)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("USER", "first question"),
                        org.assertj.core.groups.Tuple.tuple("ASSISTANT", "Hello!"));
    }

    @Test
    void onlyTheLast12HistoryEntriesAreSent() throws Exception {
        String conversationId = newConversation("q0");
        for (int i = 1; i <= 8; i++) {
            chat(customerId, conversationId, "q" + i).andExpect(status().isOk());
        }

        chat(customerId, conversationId, "q9").andExpect(status().isOk());

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentServer, times(10)).chat(any(), captor.capture());
        List<AgentChatRequest.HistoryEntry> history = captor.getAllValues().get(9).history();
        assertThat(history).hasSize(12);
        assertThat(history.get(0).text()).isEqualTo("q3");
    }

    @Test
    void anotherUsersConversationIdIsTreatedAsANewConversation() throws Exception {
        String conversationId = newConversation("my secret question");

        String body = chat(otherCustomerId, conversationId, "hello").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.conversationId")).isNotEqualTo(conversationId);
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentServer, times(2)).chat(any(), captor.capture());
        assertThat(captor.getAllValues().get(1).history()).isEmpty();
        // and reading it is indistinguishable from "does not exist"
        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), otherCustomerId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages", hasSize(0)));
    }

    @Test
    void anUnknownConversationIdStartsANewOne() throws Exception {
        chat(customerId, "5b0c1d0e-0000-4000-8000-000000000001", "hello").andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId", not("5b0c1d0e-0000-4000-8000-000000000001")));
    }

    @Test
    void invalidMessagesAreRejectedWith400BeforeAnythingIsCalled() throws Exception {
        chat(customerId, null, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").isNotEmpty());
        chat(customerId, null, "x".repeat(1001)).andExpect(status().isBadRequest());
        chat(customerId, null, "bad\u0007bell").andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/v1/assistant/chat"), customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":null}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/v1/assistant/chat"), customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"not-a-uuid\",\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest());
        verify(agentServer, never()).chat(any(), any());
    }

    @Test
    void aMessageOfExactly1000CharactersAfterTrimmingIsAccepted() throws Exception {
        chat(customerId, null, " " + "x".repeat(1000) + " ").andExpect(status().isOk());
    }

    @Test
    void newChatDeletesTheConversation() throws Exception {
        String conversationId = newConversation("hello");

        mockMvc.perform(as(delete("/api/v1/assistant/conversation").param("conversationId", conversationId), customerId))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), customerId))
                .andExpect(jsonPath("$.messages", hasSize(0)));
    }

    // ---- identity at the HTTP level

    @Test
    void withoutATokenTheAnswerIs401AsAProblemAndNothingIsCalled() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mockMvc.perform(get("/api/v1/assistant/suggestions")).andExpect(status().isUnauthorized());
        verify(agentServer, never()).chat(any(), any());
    }

    @Test
    void identityHeadersWithoutATokenAreIgnored() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat").header("X-User-Id", customerId)
                        .header("X-User-Role", "PLATFORM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"show all orders\"}"))
                .andExpect(status().isUnauthorized());
        verify(agentServer, never()).chat(any(), any());
    }

    @Test
    void aTokenThatIsNotTheirsIsRefusedWith401() throws Exception {
        List<String> refused = List.of(
                TestJwts.tokenSignedByAnotherKey(customerId, "CUSTOMER"),
                TestJwts.token(customerId, "CUSTOMER", claims -> claims.issuedAt(Instant.now().minusSeconds(960))
                        .expiresAt(Instant.now().minusSeconds(300))),
                TestJwts.token(customerId, "CUSTOMER", claims -> claims.audience(List.of("another-api"))),
                TestJwts.token(customerId, "CUSTOMER", claims -> claims.issuer("https://somebody.else")),
                TestJwts.token(customerId, "SUPERUSER"),
                TestJwts.token(customerId, "CUSTOMER", claims -> claims.subject("not-a-user-id")),
                "not.a.token");
        for (String token : refused) {
            mockMvc.perform(post("/api/v1/assistant/chat").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail", not(containsString(token))));
        }
        verify(agentServer, never()).chat(any(), any());
    }

    @Test
    void aTokenAboutToExpireIsRefusedSoTheBrowserRenewsIt() throws Exception {
        String almostExpired = TestJwts.token(customerId, "CUSTOMER",
                claims -> claims.expiresAt(Instant.now().plusSeconds(120)));

        mockMvc.perform(post("/api/v1/assistant/chat").header("Authorization", "Bearer " + almostExpired)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your session needs renewing. Please try again."));
        verify(agentServer, never()).chat(any(), any());
    }

    @Test
    void theCallersOwnTokenIsWhatGoesToTheServer() throws Exception {
        String token = TestJwts.token(customerId, "CUSTOMER");

        mockMvc.perform(post("/api/v1/assistant/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk());

        verify(agentServer).chat(argThat(identity -> identity.userId() == customerId
                && identity.role() == Role.CUSTOMER && identity.accessToken().equals(token)), any());
    }

    @Test
    void aVerifiedDeliveryPartnerGetsRiderSuggestionsAndCanAsk() throws Exception {
        long partner = NEXT_USER_ID.incrementAndGet();

        mockMvc.perform(get("/api/v1/assistant/suggestions").header("Authorization", bearer(partner, "DELIVERY_PARTNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0]").value("Which delivery is assigned to me?"));
        mockMvc.perform(post("/api/v1/assistant/chat").header("Authorization", bearer(partner, "DELIVERY_PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"which delivery is mine?\"}"))
                .andExpect(status().isOk());
        verify(agentServer).chat(org.mockito.ArgumentMatchers.argThat(identity ->
                identity.userId() == partner && identity.role() == Role.DELIVERY_PARTNER), any());
    }

    // ---- limits

    @Test
    void the21stMessageGets429WithRetryAfter() throws Exception {
        for (int i = 0; i < 20; i++) {
            chat(customerId, null, "question " + i).andExpect(status().isOk());
        }

        chat(customerId, null, "one too many").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
        chat(otherCustomerId, null, "someone else is fine").andExpect(status().isOk());
    }

    @Test
    void aFourthSimultaneousChatGets429Busy() throws Exception {
        for (int i = 0; i < 3; i++) {
            redis.opsForValue().increment("ai:rl:inflight:" + customerId); // three chats are being answered
        }

        chat(customerId, null, "hi").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("The assistant is busy, please wait for your previous question"));
        verify(agentServer, never()).chat(any(), any());
    }

    @Test
    void theSlotIsFreedAfterEveryChatEvenWhenTheServerFails() throws Exception {
        when(agentServer.chat(any(), any())).thenThrow(new AgentCallException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        chat(customerId, null, "hi").andExpect(status().isServiceUnavailable());

        assertThat(redis.opsForValue().get("ai:rl:inflight:" + customerId)).isNull();
    }

    // ---- error mapping

    @Test
    void serverFailuresAreShownWithUserSafeMessages() throws Exception {
        when(agentServer.chat(any(), any()))
                .thenThrow(new AgentCallException(HttpStatus.GATEWAY_TIMEOUT,
                        "The assistant took too long to answer. Please try again."));
        chat(customerId, null, "hi").andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.detail").value("The assistant took too long to answer. Please try again."));

        // doThrow: "when(agentServer.chat(...))" would call the mock, which already throws
        doThrow(new AgentCallException(HttpStatus.SERVICE_UNAVAILABLE,
                "The assistant is unavailable right now. Please try again in a moment."))
                .when(agentServer).chat(any(), any());
        chat(customerId, null, "hi").andExpect(status().isServiceUnavailable());
    }

    @Test
    void anUnexpectedErrorNeverLeaksDetails() throws Exception {
        when(agentServer.chat(any(), any())).thenThrow(new IllegalStateException("jdbc://secret-host/password=abc"));

        chat(customerId, null, "hi").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail", not(containsString("secret"))));
    }

    @Test
    void aFailedChatStoresNothing() throws Exception {
        String conversationId = newConversation("first");
        when(agentServer.chat(any(), any())).thenThrow(new AgentCallException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        chat(customerId, conversationId, "second").andExpect(status().isServiceUnavailable());

        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), customerId))
                .andExpect(jsonPath("$.messages", hasSize(2)));
    }

    // ---- actions

    @Test
    void confirmIsForwardedWithTheVerifiedIdentityAndClearsThePendingAction() throws Exception {
        when(agentServer.chat(any(), any())).thenReturn(reply("Please confirm", "[]", "{\"actionId\":\"act-1\"}"));
        String conversationId = newConversation("order a biryani");
        when(agentServer.confirm(any(), eq("act-1"))).thenReturn(created("CREATED"));

        mockMvc.perform(as(post("/api/v1/assistant/actions/act-1/confirm"), customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.order.id").value("55"))
                .andExpect(jsonPath("$.link.path").value("/orders/55"));

        verify(agentServer).confirm(caller(customerId, Role.CUSTOMER), eq("act-1"));
        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), customerId))
                .andExpect(jsonPath("$.messages", hasSize(3)))
                .andExpect(jsonPath("$.messages[1].pendingAction").value(nullValue()))
                .andExpect(jsonPath("$.messages[2].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[2].text").value("Your order is placed"))
                .andExpect(jsonPath("$.messages[2].links[0].path").value("/orders/55"));
    }

    @Test
    void pressingConfirmTwiceReturnsTheServersAnswerAndAddsOnlyOneMessage() throws Exception {
        when(agentServer.chat(any(), any())).thenReturn(reply("Please confirm", "[]", "{\"actionId\":\"act-2\"}"));
        String conversationId = newConversation("order");
        when(agentServer.confirm(any(), eq("act-2"))).thenReturn(created("CREATED")).thenReturn(created("ALREADY_CREATED"));

        mockMvc.perform(as(post("/api/v1/assistant/actions/act-2/confirm"), customerId))
                .andExpect(jsonPath("$.status").value("CREATED"));
        mockMvc.perform(as(post("/api/v1/assistant/actions/act-2/confirm"), customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_CREATED"))
                .andExpect(jsonPath("$.order.id").value("55"));

        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), customerId))
                .andExpect(jsonPath("$.messages", hasSize(3)));
    }

    @Test
    void aDoneChangeOfAnotherRoleAddsItsMessageOnceAndAReplayAddsNone() throws Exception {
        long partner = NEXT_USER_ID.incrementAndGet();
        when(agentServer.chat(any(), any())).thenReturn(reply("Press Picked up", "[]",
                "{\"actionId\":\"act_5\",\"kind\":\"MARK_PICKED_UP\",\"details\":{\"title\":\"Picked up\"}}"));
        String body = mockMvc.perform(as(post("/api/v1/assistant/chat"), partner, "DELIVERY_PARTNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"I have the food\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.pendingAction.details.title").value("Picked up"))
                .andReturn().getResponse().getContentAsString();
        String conversationId = JsonPath.read(body, "$.conversationId");
        ActionConfirmResponse done = new ActionConfirmResponse("DONE", null, "Delivery #31 is marked picked up.",
                new ActionConfirmResponse.Link("Open delivery", "/partner/deliveries/31"));
        when(agentServer.confirm(any(), eq("act_5"))).thenReturn(done)
                .thenReturn(new ActionConfirmResponse("ALREADY_DONE", null, done.message(), done.link()));

        mockMvc.perform(as(post("/api/v1/assistant/actions/act_5/confirm"), partner, "DELIVERY_PARTNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.order").value(nullValue()));
        mockMvc.perform(as(post("/api/v1/assistant/actions/act_5/confirm"), partner, "DELIVERY_PARTNER"))
                .andExpect(jsonPath("$.status").value("ALREADY_DONE"));

        verify(agentServer, times(2))
                .confirm(caller(partner, Role.DELIVERY_PARTNER), eq("act_5"));
        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), partner,
                        "DELIVERY_PARTNER"))
                .andExpect(jsonPath("$.messages", hasSize(3)))
                .andExpect(jsonPath("$.messages[1].pendingAction").value(nullValue()))
                .andExpect(jsonPath("$.messages[2].text").value("Delivery #31 is marked picked up."))
                .andExpect(jsonPath("$.messages[2].links[0].path").value("/partner/deliveries/31"));
    }

    @Test
    void cancelIsForwardedAndClearsThePendingAction() throws Exception {
        when(agentServer.chat(any(), any())).thenReturn(reply("Please confirm", "[]", "{\"actionId\":\"act-3\"}"));
        String conversationId = newConversation("order");

        mockMvc.perform(as(post("/api/v1/assistant/actions/act-3/cancel"), customerId)).andExpect(status().isNoContent());

        verify(agentServer).cancel(caller(customerId, Role.CUSTOMER), eq("act-3"));
        mockMvc.perform(as(get("/api/v1/assistant/conversation").param("conversationId", conversationId), customerId))
                .andExpect(jsonPath("$.messages[1].pendingAction").value(nullValue()));
    }

    @Test
    void aMissingOrForeignActionKeepsTheServersStatus() throws Exception {
        when(agentServer.confirm(any(), eq("other"))).thenThrow(new AgentCallException(HttpStatus.NOT_FOUND,
                "This action was not found or has expired."));
        when(agentServer.confirm(any(), eq("old"))).thenThrow(new AgentCallException(HttpStatus.GONE,
                "This action has expired. Please ask again."));

        mockMvc.perform(as(post("/api/v1/assistant/actions/other/confirm"), customerId)).andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/v1/assistant/actions/old/confirm"), customerId)).andExpect(status().isGone());
    }

    @Test
    void actionsNeedAVerifiedCallerAndAValidId() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/actions/act-1/confirm")).andExpect(status().isUnauthorized());
        mockMvc.perform(as(post("/api/v1/assistant/actions/bad%20id%3Fx/confirm"), customerId))
                .andExpect(status().isBadRequest());
        verify(agentServer, never()).confirm(any(), any());
    }

    // ---- suggestions and status

    @Test
    void suggestionsDependOnTheRole() throws Exception {
        long admin = NEXT_USER_ID.incrementAndGet();
        long owner = NEXT_USER_ID.incrementAndGet();

        mockMvc.perform(get("/api/v1/assistant/suggestions").header("Authorization", bearer(admin, "PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0]").value("How many orders were placed today?"))
                .andExpect(jsonPath("$.suggestions", hasSize(4)));
        mockMvc.perform(get("/api/v1/assistant/suggestions").header("Authorization", bearer(owner, "RESTAURANT_ADMIN")))
                .andExpect(jsonPath("$.suggestions[0]").value("How many orders did my restaurant deliver today?"));
        mockMvc.perform(as(get("/api/v1/assistant/suggestions"), customerId))
                .andExpect(jsonPath("$.suggestions[3]").value("I want to order a biryani"));
    }

    @Test
    void statusIsPassedThrough() throws Exception {
        when(agentServer.status()).thenReturn(new StatusResponse(true, "qwen3:4b", null));

        mockMvc.perform(get("/api/v1/assistant/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.model").value("qwen3:4b"));
    }

    @Test
    void statusNeverFails() throws Exception {
        when(agentServer.status()).thenThrow(new AgentCallException(HttpStatus.SERVICE_UNAVAILABLE, "down"))
                .thenThrow(new IllegalStateException("boom"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/assistant/status")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.model").value(nullValue()))
                    .andExpect(jsonPath("$.message").value("The assistant is not available right now."));
        }
    }

    // ---- helpers

    private org.springframework.test.web.servlet.ResultActions chat(long userId, String conversationId, String message)
            throws Exception {
        ObjectNodeBuilder body = new ObjectNodeBuilder(jsonMapper);
        String json = body.chat(conversationId, message);
        return mockMvc.perform(as(post("/api/v1/assistant/chat"), userId).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String newConversation(String message) throws Exception {
        String body = chat(customerId, null, message).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.conversationId");
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId) {
        return as(request, userId, "CUSTOMER");
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId, String role) {
        return request.header("Authorization", bearer(userId, role));
    }

    private static String bearer(long userId, String role) {
        return "Bearer " + TestJwts.token(userId, role);
    }

    /** Matches the caller by who they are (each request carries a freshly signed token). */
    private static CallerIdentity caller(long userId, Role role) {
        return argThat(identity -> identity.userId() == userId && identity.role() == role);
    }

    private AgentChatResponse reply(String text, String cardsJson, String pendingActionJson) {
        JsonNode pending = pendingActionJson == null ? null : jsonMapper.readTree(pendingActionJson);
        return new AgentChatResponse(text, jsonMapper.readTree(cardsJson), jsonMapper.readTree("[]"), pending,
                List.of("search_foods"), "test-model", 1500L, 1);
    }

    private static ActionConfirmResponse created(String status) {
        return new ActionConfirmResponse(status, new ActionConfirmResponse.Order("55", "PLACED",
                new java.math.BigDecimal("250.50"), "INR"), "Your order is placed",
                new ActionConfirmResponse.Link("View order", "/orders/55"));
    }

    /** Builds the request JSON with proper escaping (messages contain quotes and control characters). */
    private record ObjectNodeBuilder(JsonMapper mapper) {

        String chat(String conversationId, String message) {
            var node = mapper.createObjectNode();
            if (conversationId == null) {
                node.putNull("conversationId");
            } else {
                node.put("conversationId", conversationId);
            }
            node.put("message", message);
            return node.toString();
        }
    }
}
