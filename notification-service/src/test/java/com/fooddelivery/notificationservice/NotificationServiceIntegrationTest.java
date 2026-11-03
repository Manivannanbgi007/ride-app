package com.fooddelivery.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.events.user.UserRegistered;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient.RestaurantSummary;
import com.fooddelivery.notificationservice.client.UserServiceClient;
import com.fooddelivery.notificationservice.client.UserServiceClient.UserContact;
import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.entity.NotificationType;
import com.fooddelivery.notificationservice.exception.RemoteServiceException;
import com.fooddelivery.notificationservice.repository.NotificationRepository;
import com.fooddelivery.notificationservice.service.EventNotificationService;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full application against a real MySQL, Kafka and Mailpit (Testcontainers). user-service and
 * restaurant-service are mocked; the platform's events are published to Kafka by the tests, and the
 * emails are read back from Mailpit's HTTP API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** What user-service would answer, per user id. */
    private final Map<Long, UserContact> users = new ConcurrentHashMap<>();
    private volatile boolean userServiceDown;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EventNotificationService eventNotificationService;
    @Autowired
    private GenericContainer<?> mailpitContainer;
    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private RestaurantServiceClient restaurantServiceClient;

    @BeforeEach
    void fakeOtherServices() {
        when(userServiceClient.findUser(anyLong())).thenAnswer(invocation -> {
            if (userServiceDown) {
                throw new RemoteServiceException("user-service", new RuntimeException("Connection refused"));
            }
            return Optional.ofNullable(users.get(invocation.<Long>getArgument(0)));
        });
        // Restaurant r is owned by user r + 1 (see givenOwnerOf)
        when(restaurantServiceClient.findRestaurant(anyLong())).thenAnswer(invocation -> {
            long restaurantId = invocation.getArgument(0);
            return Optional.of(new RestaurantSummary(restaurantId, restaurantId + 1, "Spice Hub", "ACTIVE"));
        });
    }

    @Test
    void aNewUserGetsAWelcomeEmailAndTextMessageExactlyOnce() throws Exception {
        long userId = randomId();
        String email = "kavya-" + userId + "@example.com";
        UserRegistered registered = UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setUserId(userId).setFullName("Kavya Rao").setEmail(email).setPhone("+919812345678")
                .setRole("CUSTOMER")
                .build();

        publish(Topics.USER_REGISTERED, userId, registered);

        awaitStatus(userId, NotificationType.WELCOME, NotificationChannel.EMAIL, NotificationStatus.SENT);
        awaitStatus(userId, NotificationType.WELCOME, NotificationChannel.SMS, NotificationStatus.SENT);
        List<JsonNode> mails = mailsTo(email);
        assertThat(mails).singleElement()
                .satisfies(mail -> assertThat(mail.get("Subject").asString()).isEqualTo("Welcome to Food Delivery, Kavya Rao!"));

        // Kafka delivers the same event again: nobody is notified twice
        eventNotificationService.onUserRegistered(registered);
        assertThat(notificationsOf(userId)).hasSize(2);
    }

    @Test
    void aPlacedOrderNotifiesTheCustomerAndTheRestaurantOwnerWhoSeesItInTheInbox() throws Exception {
        long customerId = givenUser("Priya Sharma", "+919876543210");
        long restaurantId = randomId();
        long ownerId = givenOwnerOf(restaurantId);
        long orderId = randomId();

        publish(Topics.ORDER_STATUS_CHANGED, orderId, orderStatusChanged(orderId, customerId, restaurantId, "PLACED"));

        awaitStatus(customerId, NotificationType.ORDER_PLACED, NotificationChannel.EMAIL, NotificationStatus.SENT);
        awaitStatus(customerId, NotificationType.ORDER_PLACED, NotificationChannel.PUSH, NotificationStatus.SENT);
        await().atMost(TIMEOUT).until(() -> notificationsOf(ownerId).stream()
                .anyMatch(n -> n.getType() == NotificationType.NEW_ORDER_FOR_RESTAURANT
                        && n.getStatus() == NotificationStatus.SENT));
        String mailText = mailText(mailsTo(users.get(customerId).email()).getFirst());
        assertThat(mailText).startsWith("Hi Priya Sharma,").contains("Spice Hub has received your order");

        // The owner's in-app inbox shows the new order
        mockMvc.perform(get("/api/v1/notifications").with(TestTokens.as(ownerId, "RESTAURANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("NEW_ORDER_FOR_RESTAURANT"))
                .andExpect(jsonPath("$.content[0].title").value("New order #" + orderId));

        // The customer reads the push notification: the unread badge goes back to 0
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(TestTokens.as(customerId, "CUSTOMER")))
                .andExpect(jsonPath("$.unread").value(1));
        long pushId = notificationsOf(customerId).stream()
                .filter(n -> n.getChannel() == NotificationChannel.PUSH).findFirst().orElseThrow().getId();
        mockMvc.perform(put("/api/v1/notifications/{id}/read", pushId).with(TestTokens.as(customerId, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists());
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(TestTokens.as(customerId, "CUSTOMER")))
                .andExpect(jsonPath("$.unread").value(0));

        // Nobody else can read it
        mockMvc.perform(put("/api/v1/notifications/{id}/read", pushId).with(TestTokens.as(ownerId, "RESTAURANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCustomerWithoutAPhoneGetsNoTextMessageButStillThePush() throws Exception {
        long customerId = givenUser("Ravi Kumar", null);
        long orderId = randomId();

        publish(Topics.ORDER_STATUS_CHANGED, orderId, orderStatusChanged(orderId, customerId, randomId(), "OUT_FOR_DELIVERY"));

        awaitStatus(customerId, NotificationType.ORDER_OUT_FOR_DELIVERY, NotificationChannel.PUSH, NotificationStatus.SENT);
        Notification sms = awaitStatus(customerId, NotificationType.ORDER_OUT_FOR_DELIVERY, NotificationChannel.SMS,
                NotificationStatus.SKIPPED);
        assertThat(sms.getLastError()).contains("no address for SMS");
    }

    @Test
    void aCancelledPaidOrderTellsTheCustomerAboutTheRefund() throws Exception {
        long customerId = givenUser("Meera Iyer", null);
        long orderId = randomId();

        publish(Topics.ORDER_CANCELLED, orderId, OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customerId).setRestaurantId(randomId())
                .setFinalStatus("REJECTED").setPreviousStatus("PLACED").setCancelledBy("RESTAURANT")
                .setReason("Kitchen closed early").setPaymentMethod("UPI").setPaymentStatus("PAID")
                .setTotalAmount(new BigDecimal("353.95")).setCurrency("INR")
                .build());

        awaitStatus(customerId, NotificationType.ORDER_CANCELLED, NotificationChannel.EMAIL, NotificationStatus.SENT);
        JsonNode mail = mailsTo(users.get(customerId).email()).getFirst();
        assertThat(mail.get("Subject").asString()).isEqualTo("Order #" + orderId + " was declined");
        assertThat(mailText(mail)).contains("Reason: Kitchen closed early.")
                .contains("Your payment of INR 353.95 will be refunded");
    }

    @Test
    void whileUserServiceIsDownANotificationIsRetriedThenFailedAndAnAdminCanResendIt() throws Exception {
        long customerId = givenUser("Arjun Mehta", null);
        long orderId = randomId();
        userServiceDown = true;

        publish(Topics.PAYMENT_STATUS_CHANGED, orderId, PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setPaymentId(randomId()).setOrderId(orderId).setCustomerId(customerId)
                .setStatus("FAILED").setMethod("CARD").setReason("Card declined by the issuing bank")
                .setAmount(new BigDecimal("353.95")).setRefundedAmount(new BigDecimal("0.00")).setCurrency("INR")
                .build());

        // Two attempts in the tests (max-attempts: 2), one second apart
        Notification failed = awaitStatus(customerId, NotificationType.PAYMENT_FAILED, NotificationChannel.PUSH,
                NotificationStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(2);
        assertThat(failed.getLastError()).contains("user-service");
        mockMvc.perform(asAdmin(get("/api/v1/admin/notifications"))
                        .param("status", "FAILED").param("orderId", String.valueOf(orderId)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // user-service is back; an admin sends it again (audited)
        userServiceDown = false;
        mockMvc.perform(asAdmin(post("/api/v1/admin/notifications/{id}/resend", failed.getId()))
                        .header("X-Request-Id", "it-resend-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"user-service was down\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
        awaitStatus(customerId, NotificationType.PAYMENT_FAILED, NotificationChannel.PUSH, NotificationStatus.SENT);
        mockMvc.perform(asAdmin(get("/api/v1/admin/notifications/{id}/audit-logs", failed.getId())))
                .andExpect(jsonPath("$.content[0].action").value("NOTIFICATION_RESENT"))
                .andExpect(jsonPath("$.content[0].oldValue").value("FAILED"))
                .andExpect(jsonPath("$.content[0].requestId").value("it-resend-001"));

        // A sent notification cannot be resent
        mockMvc.perform(asAdmin(post("/api/v1/admin/notifications/{id}/resend", failed.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminSummaryCountsTodaysNotifications() throws Exception {
        long customerId = givenUser("Sana Khan", null);
        long orderId = randomId();
        publish(Topics.ORDER_STATUS_CHANGED, orderId, orderStatusChanged(orderId, customerId, randomId(), "ACCEPTED"));
        awaitStatus(customerId, NotificationType.ORDER_ACCEPTED, NotificationChannel.PUSH, NotificationStatus.SENT);

        mockMvc.perform(asAdmin(get("/api/v1/admin/notifications/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.SENT").exists())
                .andExpect(jsonPath("$.byChannel.PUSH").exists());
    }

    // ---- helpers ----

    private long givenUser(String fullName, String phone) {
        long userId = randomId();
        users.put(userId, new UserContact(userId, fullName, "user-" + userId + "@example.com", phone, "CUSTOMER", "ACTIVE"));
        return userId;
    }

    private long givenOwnerOf(long restaurantId) {
        long ownerId = restaurantId + 1;
        users.put(ownerId, new UserContact(ownerId, "Arjun Owner", "owner-" + ownerId + "@example.com", null,
                "RESTAURANT_ADMIN", "ACTIVE"));
        return ownerId;
    }

    private static OrderStatusChanged orderStatusChanged(long orderId, long customerId, long restaurantId, String status) {
        return OrderStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customerId).setRestaurantId(restaurantId).setRestaurantName("Spice Hub")
                .setStatus(status).setChangedBy("SYSTEM")
                .build();
    }

    private void publish(String topic, long key, SpecificRecord event) throws Exception {
        kafkaTemplate.send(topic, String.valueOf(key), event).get();
    }

    private List<Notification> notificationsOf(long userId) {
        return notificationRepository.findAll().stream()
                .filter(notification -> Long.valueOf(userId).equals(notification.getRecipientUserId()))
                .toList();
    }

    private Notification awaitStatus(long userId, NotificationType type, NotificationChannel channel,
                                     NotificationStatus expected) {
        return await().atMost(TIMEOUT).until(() -> notificationsOf(userId).stream()
                        .filter(n -> n.getType() == type && n.getChannel() == channel && n.getStatus() == expected)
                        .findFirst(),
                Optional::isPresent).orElseThrow();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.with(TestTokens.as(1, "PLATFORM_ADMIN"));
    }

    /** Emails Mailpit received for an address. */
    private List<JsonNode> mailsTo(String address) throws Exception {
        String query = URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8);
        JsonNode result = jsonMapper.readTree(mailpit("/api/v1/search?query=" + query));
        return new ArrayList<>(result.get("messages").values());
    }

    private String mailText(JsonNode mail) throws Exception {
        return jsonMapper.readTree(mailpit("/api/v1/message/" + mail.get("ID").asString())).get("Text").asString();
    }

    private String mailpit(String path) throws Exception {
        URI uri = URI.create("http://" + mailpitContainer.getHost() + ":"
                + mailpitContainer.getMappedPort(TestcontainersConfiguration.MAILPIT_API_PORT) + path);
        return HTTP.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    }
}
