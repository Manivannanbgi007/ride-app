package com.fooddelivery.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.chat.ChatMessageSent;
import com.fooddelivery.events.chat.ChatMessagesRead;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient.RestaurantSummary;
import com.fooddelivery.notificationservice.client.UserServiceClient;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The live updates end to end: real Kafka events, real WebSocket connections of several users at once, against the
 * running application (MySQL and Kafka in Testcontainers; user-service and restaurant-service are mocked).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.realtime.heartbeat-interval=1s",
        "app.realtime.idle-timeout=30s",
        "app.realtime.max-sessions-per-user=3",
        // The one web page the tests connect from (independent of the default and of REALTIME_ALLOWED_ORIGINS)
        "app.realtime.allowed-origins=" + LiveUpdatesIntegrationTest.ALLOWED_ORIGIN})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LiveUpdatesIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final String ALLOWED_ORIGIN = "http://localhost:5180";

    @LocalServerPort
    private int port;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private RestaurantServiceClient restaurantServiceClient;

    private final List<Client> opened = new ArrayList<>();

    /** Real access tokens (TestJwts), checked with that helper's public key as user-service's in production */
    @DynamicPropertySource
    static void tokensOfTheTests(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwts::jwksUri);
    }

    @BeforeEach
    void fakeOtherServices() {
        // Restaurant r is owned by user r + 1000
        when(restaurantServiceClient.findOwnedRestaurantIds(anyLong()))
                .thenAnswer(invocation -> Set.of(invocation.<Long>getArgument(0) - 1000));
        when(restaurantServiceClient.findRestaurant(anyLong())).thenAnswer(invocation -> {
            long restaurantId = invocation.getArgument(0);
            return Optional.of(new RestaurantSummary(restaurantId, restaurantId + 1000, "Spice Hub", "ACTIVE"));
        });
        when(userServiceClient.findUser(anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void closeConnections() {
        opened.forEach(Client::close);
    }

    @Test
    void everyoneIsSentOnlyWhatConcernsThemAcrossRoles() throws Exception {
        long customer = randomId();
        long otherCustomer = randomId();
        long restaurant = randomId();
        Client mine = connect(customer, "CUSTOMER");
        Client someoneElse = connect(otherCustomer, "CUSTOMER");
        Client owner = connect(restaurant + 1000, "RESTAURANT_ADMIN");
        Client otherOwner = connect(randomId() + 5000, "RESTAURANT_ADMIN");
        Client admin = connect(1, "PLATFORM_ADMIN");
        waitUntilEventsArrive(admin);

        long orderId = randomId();
        publish(Topics.ORDER_STATUS_CHANGED, orderId, orderStatusChanged(orderId, customer, restaurant, "PREPARING"));

        for (Client concerned : List.of(mine, owner, admin)) {
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(concerned.eventsAbout(orderId)).hasSize(1));
        }
        assertThat(mine.eventsAbout(orderId).getFirst())
                .contains("\"type\":\"ORDER_STATUS_CHANGED\"", "\"status\":\"PREPARING\"")
                .doesNotContain("customerId");

        // A payment is the customer's business, not the restaurant's
        publish(Topics.PAYMENT_STATUS_CHANGED, orderId, paymentStatusChanged(orderId, customer));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(mine.eventsAbout(orderId)).hasSize(2));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(admin.eventsAbout(orderId)).hasSize(2));
        assertThat(owner.eventsAbout(orderId)).hasSize(1);

        // Nobody else got anything about this order
        assertThat(someoneElse.eventsAbout(orderId)).isEmpty();
        assertThat(otherOwner.eventsAbout(orderId)).isEmpty();
    }

    @Test
    void aChatMessageReachesItsParticipantAndAnAdminButNotAnotherCustomer() throws Exception {
        long participant = randomId();
        Client mine = connect(participant, "CUSTOMER");
        Client someoneElse = connect(randomId(), "CUSTOMER");
        Client admin = connect(1, "PLATFORM_ADMIN");
        waitUntilEventsArrive(admin);

        long conversationId = randomId();
        publish(Topics.CHAT_MESSAGE_SENT, conversationId, ChatMessageSent.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now()).setRequestId("r-1")
                .setConversationId(conversationId).setMessageId(5L).setParticipantUserId(participant)
                .setSenderUserId(participant).setFromStaff(false).build());
        publish(Topics.CHAT_MESSAGES_READ, conversationId, ChatMessagesRead.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now()).setRequestId("r-2")
                .setConversationId(conversationId).setParticipantUserId(participant).setReadByStaff(true)
                .setUpToMessageId(5L).build());

        for (Client concerned : List.of(mine, admin)) {
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(concerned.messages())
                    .anyMatch(text -> text.contains("\"type\":\"CHAT_MESSAGE\"")
                            && text.contains("\"conversationId\":" + conversationId) && text.contains("\"messageId\":5"))
                    .anyMatch(text -> text.contains("\"type\":\"CHAT_READ\"")
                            && text.contains("\"conversationId\":" + conversationId)));
        }
        assertThat(someoneElse.messages()).noneMatch(text -> text.contains("\"conversationId\":" + conversationId));
    }

    @Test
    void manyUsersAtOnceEachReceiveOnlyTheirOwnOrders() throws Exception {
        Client admin = connect(1, "PLATFORM_ADMIN");
        waitUntilEventsArrive(admin);
        List<Long> customers = new ArrayList<>();
        List<Client> clients = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            long customer = randomId();
            customers.add(customer);
            clients.add(connect(customer, "CUSTOMER"));
        }

        List<Long> orders = new ArrayList<>();
        for (long customer : customers) {
            long orderId = randomId();
            orders.add(orderId);
            publish(Topics.ORDER_STATUS_CHANGED, orderId, orderStatusChanged(orderId, customer, 1L, "ACCEPTED"));
        }

        for (int i = 0; i < clients.size(); i++) {
            Client client = clients.get(i);
            long mineOrder = orders.get(i);
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(client.eventsAbout(mineOrder)).hasSize(1));
            for (long order : orders) {
                if (order != mineOrder) {
                    assertThat(client.eventsAbout(order)).isEmpty();
                }
            }
        }
        for (long order : orders) {
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(admin.eventsAbout(order)).hasSize(1));
        }
    }

    @Test
    void anEventSentTwiceByKafkaIsReceivedOnce() throws Exception {
        long customer = randomId();
        Client mine = connect(customer, "CUSTOMER");
        Client admin = connect(1, "PLATFORM_ADMIN");
        waitUntilEventsArrive(admin);

        long orderId = randomId();
        OrderStatusChanged event = orderStatusChanged(orderId, customer, 1L, "OUT_FOR_DELIVERY");
        publish(Topics.ORDER_STATUS_CHANGED, orderId, event);
        publish(Topics.ORDER_STATUS_CHANGED, orderId, event);
        // A later, different event proves the duplicate has had time to arrive
        publish(Topics.ORDER_STATUS_CHANGED, orderId, orderStatusChanged(orderId, customer, 1L, "DELIVERED"));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(mine.eventsAbout(orderId)).hasSize(2));
        assertThat(mine.eventsAbout(orderId).stream().filter(text -> text.contains("OUT_FOR_DELIVERY"))).hasSize(1);
    }

    @Test
    void aConnectionWithoutAValidTokenOrFromAnotherSiteIsRefused() {
        assertThat(refusedWith("", ALLOWED_ORIGIN)).isEqualTo(401);
        // The old way of naming a user is not believed
        assertThat(refusedWith("userId=7&role=CUSTOMER", ALLOWED_ORIGIN)).isEqualTo(401);
        assertThat(refusedWith("access_token=not-a-token", ALLOWED_ORIGIN)).isEqualTo(401);
        assertThat(refusedWith("access_token=" + TestJwts.tokenSignedByAnotherKey(7, "CUSTOMER"), ALLOWED_ORIGIN))
                .isEqualTo(401);
        assertThat(refusedWith("access_token=" + TestJwts.token(7, "NOBODY"), ALLOWED_ORIGIN)).isEqualTo(401);
        // A valid token, but from a web page that is not the application's
        assertThat(refusedWith("access_token=" + TestJwts.token(7, "CUSTOMER"), "http://evil.example")).isEqualTo(403);
    }

    @Test
    void theServerKeepsSayingItIsThereAndAReconnectingClientIsServedAgain() throws Exception {
        long customer = randomId();
        Client first = connect(customer, "CUSTOMER");
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(first.messages())
                .anyMatch(text -> text.contains("\"kind\":\"HELLO\""))
                .anyMatch(text -> text.contains("\"kind\":\"HEARTBEAT\"")));

        // The connection drops (the client's network went away)...
        first.close();
        // ...and the application connects again: the new connection works like the first
        Client again = connect(customer, "CUSTOMER");
        Client admin = connect(1, "PLATFORM_ADMIN");
        waitUntilEventsArrive(admin);
        long orderId = randomId();
        publish(Topics.ORDER_STATUS_CHANGED, orderId, orderStatusChanged(orderId, customer, 1L, "PLACED"));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(again.eventsAbout(orderId)).hasSize(1));
        assertThat(first.eventsAbout(orderId)).isEmpty();
    }

    @Test
    void aUserOverTheLimitOfOpenTabsLosesTheOldestConnection() throws Exception {
        long customer = randomId();
        Client oldest = connect(customer, "CUSTOMER");
        connect(customer, "CUSTOMER");
        connect(customer, "CUSTOMER");
        Client newest = connect(customer, "CUSTOMER");

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(oldest.isClosed()).isTrue());
        assertThat(newest.isClosed()).isFalse();
    }

    // ---- helpers

    private Client connect(long userId, String role) throws Exception {
        // As the web application connects: the user's access token in the query
        return open("access_token=" + TestJwts.token(userId, role), ALLOWED_ORIGIN);
    }

    /** The HTTP status the server refused the connection with. */
    private int refusedWith(String query, String origin) {
        try {
            open(query, origin);
        } catch (WebSocketHandshakeException e) {
            return e.getResponse().statusCode();
        } catch (Exception e) {
            throw new IllegalStateException("Not a refused handshake", e);
        }
        throw new IllegalStateException("The connection was accepted");
    }

    private Client open(String query, String origin) throws Exception {
        Client client = new Client();
        try {
            client.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .header("Origin", origin)
                    .buildAsync(URI.create("ws://localhost:" + port + "/ws/v1/live" + (query.isEmpty() ? "" : "?" + query)),
                            client)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
        opened.add(client);
        await().atMost(TIMEOUT).until(() -> client.messages().stream().anyMatch(text -> text.contains("HELLO")));
        return client;
    }

    /**
     * The listeners start with the application but join Kafka a moment later and read only new events: send harmless
     * events until the admin (who sees everything) gets one, so the real test events cannot be missed.
     */
    private void waitUntilEventsArrive(Client admin) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(500)).until(() -> {
            long probe = randomId();
            publish(Topics.ORDER_STATUS_CHANGED, probe, orderStatusChanged(probe, 1L, 1L, "PLACED"));
            return !admin.eventsAbout(probe).isEmpty() || admin.messages().stream().anyMatch(text -> text.contains("EVENT"));
        });
    }

    private void publish(String topic, long key, SpecificRecord event) throws Exception {
        kafkaTemplate.send(topic, String.valueOf(key), event).get();
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(10_000, 900_000_000L);
    }

    private static OrderStatusChanged orderStatusChanged(long orderId, long customerId, long restaurantId, String status) {
        return OrderStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customerId).setRestaurantId(restaurantId)
                .setRestaurantName("Spice Hub").setStatus(status).setChangedBy("SYSTEM")
                .build();
    }

    private static PaymentStatusChanged paymentStatusChanged(long orderId, long customerId) {
        return PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setPaymentId(orderId).setOrderId(orderId).setCustomerId(customerId).setStatus("FAILED")
                .setMethod("CARD").setAmount(new java.math.BigDecimal("353.95"))
                .setRefundedAmount(new java.math.BigDecimal("0.00")).setCurrency("INR")
                .build();
    }

    /** A browser's view of a live connection: it collects every text message the server sends. */
    private static final class Client implements WebSocket.Listener {

        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final StringBuilder partial = new StringBuilder();
        private volatile boolean closed;
        private WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed = true;
        }

        List<String> messages() {
            return messages;
        }

        List<String> eventsAbout(long orderId) {
            return messages.stream().filter(text -> text.contains("\"orderId\":" + orderId + ",")
                    || text.contains("\"orderId\":" + orderId + "}")).toList();
        }

        boolean isClosed() {
            return closed || socket.isInputClosed();
        }

        void close() {
            if (socket != null && !socket.isOutputClosed()) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "test over");
            }
            closed = true;
        }
    }
}
