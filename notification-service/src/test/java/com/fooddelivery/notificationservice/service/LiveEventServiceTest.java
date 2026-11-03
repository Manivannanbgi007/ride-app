package com.fooddelivery.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fooddelivery.events.chat.ChatMessageSent;
import com.fooddelivery.events.chat.ChatMessagesRead;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.notificationservice.config.RealtimeProperties;
import com.fooddelivery.notificationservice.entity.Role;
import com.fooddelivery.notificationservice.web.LiveClient;
import com.fooddelivery.notificationservice.web.LiveSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/** Who is sent which live event, and that nobody is sent one twice. */
class LiveEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RealtimeProperties properties = new RealtimeProperties(true, List.of(), Duration.ofSeconds(25),
            Duration.ofSeconds(75), 5, Duration.ofSeconds(5), 65_536, 3);
    private LiveSessions sessions;
    private LiveEventService service;

    private final List<String> customer7 = new ArrayList<>();
    private final List<String> customer8 = new ArrayList<>();
    private final List<String> owner5 = new ArrayList<>();
    private final List<String> otherOwner = new ArrayList<>();
    private final List<String> partner77 = new ArrayList<>();
    private final List<String> admin = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sessions = new LiveSessions(properties, clock);
        service = new LiveEventService(sessions, JsonMapper.builder().findAndAddModules().build(), clock, properties);
        connect("c7", new LiveClient(7L, Role.CUSTOMER, Set.of()), customer7);
        connect("c8", new LiveClient(8L, Role.CUSTOMER, Set.of()), customer8);
        connect("o5", new LiveClient(5L, Role.RESTAURANT_ADMIN, Set.of(10L)), owner5);
        connect("o6", new LiveClient(6L, Role.RESTAURANT_ADMIN, Set.of(11L)), otherOwner);
        connect("p77", new LiveClient(77L, Role.DELIVERY_PARTNER, Set.of()), partner77);
        connect("a1", new LiveClient(1L, Role.PLATFORM_ADMIN, Set.of()), admin);
    }

    @Test
    void anOrderChangeReachesItsCustomerItsRestaurantOwnerAndTheAdminsAndNobodyElse() {
        service.onOrderStatusChanged(orderStatusChanged("PREPARING"));

        assertThat(customer7).hasSize(1);
        assertThat(owner5).hasSize(1);
        assertThat(admin).hasSize(1);
        assertThat(customer8).isEmpty();
        assertThat(otherOwner).isEmpty();
        assertThat(partner77).isEmpty();
    }

    @Test
    void theMessageHoldsIdsAndStatusesOnly() {
        service.onOrderStatusChanged(orderStatusChanged("PREPARING"));

        assertThat(customer7.getFirst())
                .contains("\"kind\":\"EVENT\"", "\"type\":\"ORDER_STATUS_CHANGED\"", "\"orderId\":500",
                        "\"status\":\"PREPARING\"", "\"restaurantId\":10")
                .doesNotContain("customerId", "Spice Hub", "354");
    }

    @Test
    void paymentsAreTheCustomersAndTheAdminsBusinessOnly() {
        service.onPaymentStatusChanged(PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setPaymentId(900L).setOrderId(500L).setCustomerId(7L).setStatus("FAILED").setMethod("CARD")
                .setAmount(new BigDecimal("353.95")).setRefundedAmount(new BigDecimal("0.00")).setCurrency("INR")
                .build());

        assertThat(customer7).hasSize(1);
        assertThat(admin).hasSize(1);
        assertThat(owner5).isEmpty();
        assertThat(otherOwner).isEmpty();
    }

    @Test
    void aDeliveryChangeAlsoReachesItsPartner() {
        service.onDeliveryStatusChanged(DeliveryStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setDeliveryId(40L).setOrderId(500L).setCustomerId(7L).setRestaurantId(10L)
                .setStatus("PICKED_UP").setPreviousStatus("ACCEPTED").setPartnerUserId(77L)
                .build());

        assertThat(partner77).hasSize(1);
        assertThat(customer7).hasSize(1);
        assertThat(owner5).hasSize(1);
        assertThat(admin).hasSize(1);
        assertThat(customer8).isEmpty();
        assertThat(otherOwner).isEmpty();
    }

    @Test
    void aRestaurantChangeReachesItsOwnerAndTheAdmins() {
        service.onRestaurantChanged(restaurantChanged(5L, 10L, "APPROVED"));

        assertThat(owner5).hasSize(1);
        assertThat(admin).hasSize(1);
        assertThat(otherOwner).isEmpty();
        assertThat(customer7).isEmpty();
    }

    @Test
    void aNewRestaurantOfAConnectedOwnerBringsItsOrdersFromThenOn() {
        service.onOrderStatusChanged(orderStatusChangedFor(12L));
        assertThat(owner5).isEmpty();

        service.onRestaurantChanged(restaurantChanged(5L, 12L, "PENDING"));
        owner5.clear();
        service.onOrderStatusChanged(orderStatusChangedFor(12L));

        assertThat(owner5).hasSize(1);
        assertThat(otherOwner).isEmpty();
    }

    @Test
    void anEventKafkaDeliversTwiceIsSentOnce() {
        OrderStatusChanged event = orderStatusChanged("ACCEPTED");

        service.onOrderStatusChanged(event);
        service.onOrderStatusChanged(event);

        assertThat(customer7).hasSize(1);
        assertThat(admin).hasSize(1);
    }

    @Test
    void onlyTheLatestEventIdsAreRemembered() {
        OrderStatusChanged first = orderStatusChanged("ACCEPTED");
        service.onOrderStatusChanged(first);
        // 3 are kept (see the properties above): four newer ones push the first one out
        for (int i = 0; i < 4; i++) {
            service.onOrderStatusChanged(orderStatusChanged("PREPARING"));
        }
        customer7.clear();

        service.onOrderStatusChanged(first);

        assertThat(customer7).hasSize(1);
    }

    @Test
    void aChatMessageReachesItsParticipantAndTheAdminsOnlyAsIdsOnly() {
        service.onChatMessageSent(chatMessageSent(7L, false));

        assertThat(customer7).hasSize(1);
        assertThat(admin).hasSize(1);
        assertThat(customer8).isEmpty();
        assertThat(owner5).isEmpty();
        assertThat(otherOwner).isEmpty();
        assertThat(partner77).isEmpty();
        assertThat(customer7.getFirst())
                .contains("\"kind\":\"EVENT\"", "\"type\":\"CHAT_MESSAGE\"", "\"conversationId\":31",
                        "\"messageId\":99", "\"fromStaff\":false")
                .doesNotContain("senderUserId", "participantUserId", "orderId");
    }

    @Test
    void aChatMessageToARestaurantOwnerParticipantReachesThemAndNoOtherOwner() {
        service.onChatMessageSent(chatMessageSent(5L, true));

        assertThat(owner5).hasSize(1);
        assertThat(owner5.getFirst()).contains("\"fromStaff\":true");
        assertThat(admin).hasSize(1);
        assertThat(otherOwner).isEmpty();
        assertThat(customer7).isEmpty();
    }

    @Test
    void aChatReadReachesItsParticipantAndTheAdminsOnly() {
        service.onChatMessagesRead(ChatMessagesRead.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW).setRequestId("r-1")
                .setConversationId(31L).setParticipantUserId(7L).setReadByStaff(true).setUpToMessageId(99L)
                .build());

        assertThat(customer7).hasSize(1);
        assertThat(admin).hasSize(1);
        assertThat(customer8).isEmpty();
        assertThat(owner5).isEmpty();
        assertThat(customer7.getFirst())
                .contains("\"type\":\"CHAT_READ\"", "\"conversationId\":31", "\"readByStaff\":true",
                        "\"upToMessageId\":99")
                .doesNotContain("messageId\":99,", "fromStaff");
    }

    @Test
    void aChatEventKafkaDeliversTwiceIsSentOnce() {
        ChatMessageSent event = chatMessageSent(7L, false);

        service.onChatMessageSent(event);
        service.onChatMessageSent(event);

        assertThat(customer7).hasSize(1);
        assertThat(admin).hasSize(1);
    }

    @Test
    void aUserWithSeveralConnectionsGetsTheChatEventOnEachOfThem() {
        List<String> secondTab = new ArrayList<>();
        connect("c7b", new LiveClient(7L, Role.CUSTOMER, Set.of()), secondTab);

        service.onChatMessageSent(chatMessageSent(7L, true));

        assertThat(customer7).hasSize(1);
        assertThat(secondTab).hasSize(1);
    }

    @Test
    void presenceListsTheConnectedUsersAmongTheAsked() {
        assertThat(sessions.onlineAmong(Set.of(7L, 99L, 1L))).containsExactly(1L, 7L);
        assertThat(sessions.onlineAmong(Set.of(99L, 100L))).isEmpty();
    }

    @Test
    void withNobodyConnectedNothingHappens() {
        LiveSessions empty = new LiveSessions(properties, clock);
        LiveEventService quiet = new LiveEventService(empty, JsonMapper.builder().build(), clock, properties);

        quiet.onOrderStatusChanged(orderStatusChanged("PLACED"));

        assertThat(empty.size()).isZero();
    }

    // ---- helpers

    private void connect(String id, LiveClient client, List<String> inbox) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        try {
            doAnswer(invocation -> {
                WebSocketMessage<?> message = invocation.getArgument(0);
                if (message instanceof TextMessage text) {
                    inbox.add(text.getPayload());
                }
                return null;
            }).when(session).sendMessage(org.mockito.ArgumentMatchers.any());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        sessions.add(session, client);
    }

    private static ChatMessageSent chatMessageSent(long participantUserId, boolean fromStaff) {
        return ChatMessageSent.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW).setRequestId("r-1")
                .setConversationId(31L).setMessageId(99L).setParticipantUserId(participantUserId)
                .setSenderUserId(fromStaff ? 1L : participantUserId).setFromStaff(fromStaff)
                .build();
    }

    private static OrderStatusChanged orderStatusChanged(String status) {
        return OrderStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setOrderId(500L).setCustomerId(7L).setRestaurantId(10L).setRestaurantName("Spice Hub")
                .setStatus(status).setChangedBy("SYSTEM")
                .build();
    }

    private static OrderStatusChanged orderStatusChangedFor(long restaurantId) {
        return OrderStatusChanged.newBuilder(orderStatusChanged("PLACED")).setEventId(UUID.randomUUID().toString())
                .setRestaurantId(restaurantId).build();
    }

    private static RestaurantChanged restaurantChanged(long ownerUserId, long restaurantId, String status) {
        return RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setChangeType("STATUS_CHANGED").setRestaurantId(restaurantId).setVersion(2L)
                .setOwnerUserId(ownerUserId).setName("Spice Hub").setCity("Bengaluru").setStatus(status)
                .build();
    }
}
