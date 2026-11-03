package com.fooddelivery.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderCreatedItem;
import com.fooddelivery.events.order.OrderPaymentRejected;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.paymentservice.client.OrderServiceClient;
import com.fooddelivery.paymentservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.exception.ConflictException;
import com.fooddelivery.paymentservice.exception.RemoteServiceException;
import com.fooddelivery.paymentservice.repository.OutboxEventRepository;
import com.fooddelivery.paymentservice.repository.PayableOrderRepository;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.fooddelivery.paymentservice.service.OrderEventService;
import com.fooddelivery.paymentservice.service.PaymentService;
import com.fooddelivery.paymentservice.service.RefundService;
import com.jayway.jsonpath.JsonPath;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Full application against a real MySQL and Kafka (Testcontainers). order-service is replaced by a small in-memory
 * fake for its order lookup (UP or DOWN); its events are published to Kafka by the tests, and payment results are
 * read from the outbox and from Kafka.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// Closes the context (and stops its MySQL and Kafka) afterwards: RazorpayPaymentIntegrationTest needs other settings,
// so it starts its own; without this both pairs of containers would run at the same time
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentServiceIntegrationTest {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(20);
    private static final BigDecimal TOTAL = new BigDecimal("353.95");

    private final Map<Long, OrderSummary> orders = new ConcurrentHashMap<>();
    private volatile boolean orderServiceDown;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private RefundService refundService;
    @Autowired
    private OrderEventService orderEventService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PayableOrderRepository payableOrderRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private OrderServiceClient orderServiceClient;

    @BeforeEach
    void fakeOrderService() {
        when(orderServiceClient.findOrder(anyLong())).thenAnswer(invocation -> {
            if (orderServiceDown) {
                throw new RemoteServiceException("order-service", new RuntimeException("Connection refused"));
            }
            return Optional.ofNullable(orders.get(invocation.<Long>getArgument(0)));
        });
    }

    @Test
    void customerPaysOnceAndOrderServiceIsToldThroughTheOutbox() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        String key = "pay-" + UUID.randomUUID();

        String body = pay(customer, key, upiBody(orderId, "meera@okhdfc"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(353.95))
                .andExpect(jsonPath("$.instrument").value("UPI me***@okhdfc"))
                .andReturn().getResponse().getContentAsString();
        long paymentId = ((Number) JsonPath.read(body, "$.id")).longValue();

        pay(customer, key, upiBody(orderId, "meera@okhdfc"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(paymentId));

        // The result was published once (saved with the payment in one transaction, then sent by the relay)
        PaymentStatusChanged event = KafkaTestEvents.awaitEvent(consumerFactory, Topics.PAYMENT_STATUS_CHANGED,
                String.valueOf(orderId), PaymentStatusChanged.class);
        assertThat(event.getPaymentId()).isEqualTo(paymentId);
        assertThat(event.getStatus()).isEqualTo("SUCCESS");
        assertThat(event.getAmount()).isEqualByComparingTo(TOTAL);
        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("PAYMENT", paymentId)).hasSize(1);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].eventType").value("PaymentStatusChanged"))
                .andExpect(jsonPath("$.events[0].status").value("PUBLISHED")));
    }

    @Test
    void declinedCardCanBeRetriedWithAnotherCard() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "CARD");

        pay(customer, "pay-" + UUID.randomUUID(), cardBody(orderId, "4000000000000002"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Card declined by the issuing bank"))
                .andExpect(jsonPath("$.instrument").value("VISA **** 0002"));
        PaymentStatusChanged failed = KafkaTestEvents.awaitEvent(consumerFactory, Topics.PAYMENT_STATUS_CHANGED,
                String.valueOf(orderId), PaymentStatusChanged.class);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getReason()).isEqualTo("Card declined by the issuing bank");

        pay(customer, "pay-" + UUID.randomUUID(), cardBody(orderId, "4111111111111111"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void anOrderKnownFromItsEventIsPaidEvenWhileOrderServiceIsDown() throws Exception {
        long customer = randomId();
        long orderId = randomId();
        publish(Topics.ORDER_CREATED, orderId, orderCreated(orderId, customer, "WALLET"));
        await().atMost(EVENT_TIMEOUT).until(() -> payableOrderRepository.existsById(orderId));
        orderServiceDown = true;

        pay(customer, "pay-" + UUID.randomUUID(), walletBody(orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(353.95));

        // An order not known locally yet still needs order-service: never guess the amount
        pay(customer, "pay-" + UUID.randomUUID(), walletBody(randomId()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void anOrderCancelledBeforeItWasPaidCanNoLongerBePaid() throws Exception {
        long customer = randomId();
        long orderId = randomId();
        publish(Topics.ORDER_CREATED, orderId, orderCreated(orderId, customer, "UPI"));
        publish(Topics.ORDER_CANCELLED, orderId, orderCancelled(orderId, customer, "CANCELLED", "CUSTOMER", "PENDING"));
        await().atMost(EVENT_TIMEOUT).until(() -> payableOrderRepository.findById(orderId)
                .map(order -> "CANCELLED".equals(order.getStatus())).orElse(false));

        pay(customer, "pay-" + UUID.randomUUID(), upiBody(orderId, "meera@okhdfc"))
                .andExpect(status().isConflict());
        assertThat(paymentRepository.findAll().stream().filter(payment -> payment.getOrderId() == orderId)).isEmpty();
    }

    @Test
    void paidOrderCancelledInOrderServiceIsRefundedExactlyOnce() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        long paymentId = paidOrder(customer, orderId, "meera@okhdfc");

        // In order-service the restaurant rejects the paid order, which publishes OrderCancelled
        setOrderStatus(orderId, "REJECTED");
        OrderCancelled cancelled = orderCancelled(orderId, customer, "REJECTED", "RESTAURANT", "PAID");
        publish(Topics.ORDER_CANCELLED, orderId, cancelled);

        awaitPaymentStatus(paymentId, "REFUNDED");
        asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.payment.refundedAmount").value(353.95))
                .andExpect(jsonPath("$.refunds.length()").value(1))
                .andExpect(jsonPath("$.refunds[0].requestedByType").value("SYSTEM"))
                .andExpect(jsonPath("$.refunds[0].reason")
                        .value("Order " + orderId + " rejected by restaurant: Kitchen closed early"));
        // order-service is told: SUCCESS, then REFUNDED
        assertThat(KafkaTestEvents.awaitEvents(consumerFactory, Topics.PAYMENT_STATUS_CHANGED, String.valueOf(orderId),
                PaymentStatusChanged.class, 2)).extracting(PaymentStatusChanged::getStatus)
                .containsExactly("SUCCESS", "REFUNDED");

        // Kafka delivers the same event again: it is recognised (processed_events) and refunds nothing more
        orderEventService.onOrderCancelled(cancelled);
        asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.refunds.length()").value(1));
    }

    @Test
    void aPaymentOrderServiceRejectedIsRefunded() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        long paymentId = paidOrder(customer, orderId, "meera@okhdfc");

        // The customer had cancelled while the payment was running: order-service rejects the late payment
        publish(Topics.ORDER_PAYMENT_REJECTED, orderId, OrderPaymentRejected.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setOrderId(orderId)
                .setPaymentId(paymentId)
                .setOrderStatus("CANCELLED")
                .setReason("Order " + orderId + " was cancelled before the payment completed")
                .build());

        awaitPaymentStatus(paymentId, "REFUNDED");
        asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.refunds.length()").value(1))
                .andExpect(jsonPath("$.refunds[0].requestedByType").value("SYSTEM"))
                .andExpect(jsonPath("$.refunds[0].reason")
                        .value("Order " + orderId + " was cancelled before the payment completed"));
    }

    @Test
    void aRefundThatNeverReachedTheProviderIsSentByTheRecoveryJob() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        long paymentId = paidOrder(customer, orderId, "meera@okhdfc");

        // The refund request was saved, then the service stopped before calling the provider
        Long refundId = transactionTemplate.execute(transaction ->
                refundService.requestAutomaticRefund(paymentId, "Order cancelled").orElseThrow());
        asAdmin(get("/api/v1/admin/refunds/{r}", refundId)).andExpect(jsonPath("$.status").value("REQUESTED"));

        assertThat(refundService.processStuckRefunds(Duration.ZERO)).isGreaterThanOrEqualTo(1);

        asAdmin(get("/api/v1/admin/refunds/{r}", refundId)).andExpect(jsonPath("$.status").value("SUCCESS"));
        awaitPaymentStatus(paymentId, "REFUNDED");
    }

    @Test
    void adminRefundsPartlyThenFullyWithAuditAndIdempotency() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        long paymentId = paidOrder(customer, orderId, "meera@okhdfc");

        // A running order cannot be refunded
        refund(paymentId, "refund-" + UUID.randomUUID(), "{\"reason\": \"Customer asked\"}").andExpect(status().isConflict());

        setOrderStatus(orderId, "CANCELLED");
        String key = "refund-" + UUID.randomUUID();
        refund(paymentId, key, "{\"amount\": 100.00, \"reason\": \"Missing items\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.requestedByType").value("ADMIN"))
                .andExpect(jsonPath("$.completedAt").exists());
        refund(paymentId, key, "{\"amount\": 100.00, \"reason\": \"Missing items\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.payment.refundedAmount").value(100.00));

        // The rest (no amount = everything left): the payment is REFUNDED and order-service is told
        refund(paymentId, "refund-" + UUID.randomUUID(), "{\"reason\": \"Order cancelled\"}")
                .andExpect(jsonPath("$.amount").value(253.95));
        asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.events.length()").value(2));

        asAdmin(get("/api/v1/admin/payments/{p}/audit-logs", paymentId))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("REFUND_INITIATED"));
        asAdmin(get("/api/v1/admin/refunds").param("paymentId", String.valueOf(paymentId)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void failedRefundReleasesTheAmountForAnotherTry() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        long paymentId = paidOrder(customer, orderId, "refundfail@upi");
        setOrderStatus(orderId, "REJECTED");

        refund(paymentId, "refund-" + UUID.randomUUID(), "{\"reason\": \"Restaurant rejected\"}")
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Provider could not refund to this account"));
        asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.payment.refundedAmount").value(0.00));
    }

    /** Two payments for the same order at the same moment (two tabs, double click): only one can charge. */
    @Test
    void anOrderIsNeverChargedTwiceEvenWithSimultaneousPayments() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");

        CountDownLatch startTogether = new CountDownLatch(1);
        Callable<String> attempt = () -> {
            startTogether.await();
            try {
                return paymentService.pay(customer, "pay-" + UUID.randomUUID(),
                        new PayRequest(orderId, PaymentMethod.UPI, null, "meera@okhdfc", null)).payment().status().name();
            } catch (ConflictException e) {
                return "REFUSED";
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(attempt);
            Future<String> second = pool.submit(attempt);
            startTogether.countDown();
            assertThat(List.of(result(first), result(second))).containsExactlyInAnyOrder("SUCCESS", "REFUSED");
        } finally {
            pool.shutdownNow();
        }
        assertThat(paymentRepository.findAll().stream().filter(payment -> payment.getOrderId() == orderId)).hasSize(1);
    }

    @Test
    void summaryShowsWhatWasCollectedToday() throws Exception {
        long customer = randomId();
        pay(customer, "pay-" + UUID.randomUUID(), upiBody(newOrder(customer, "UPI"), "meera@okhdfc"));

        asAdmin(get("/api/v1/admin/payments/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.SUCCESS.count").exists())
                .andExpect(jsonPath("$.byMethod.UPI.count").exists());
    }

    // ---- helpers ----

    private long paidOrder(long customer, long orderId, String upiId) throws Exception {
        String body = pay(customer, "pay-" + UUID.randomUUID(), upiBody(orderId, upiId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void awaitPaymentStatus(long paymentId, String status) {
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> asAdmin(get("/api/v1/admin/payments/{p}", paymentId))
                .andExpect(jsonPath("$.payment.status").value(status)));
    }

    private void publish(String topic, long orderId, SpecificRecord event) throws Exception {
        kafkaTemplate.send(topic, String.valueOf(orderId), event).get();
    }

    private static OrderCreated orderCreated(long orderId, long customerId, String paymentMethod) {
        return OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setOrderId(orderId)
                .setCustomerId(customerId)
                .setRestaurantId(10L)
                .setStatus("PAYMENT_PENDING")
                .setPaymentMethod(paymentMethod)
                .setItems(List.of(OrderCreatedItem.newBuilder()
                        .setMenuItemId(100L).setName("Chicken Biryani").setQuantity(1)
                        .setUnitPrice(new BigDecimal("299.00")).build()))
                .setItemsTotal(new BigDecimal("299.00"))
                .setDeliveryFee(new BigDecimal("40.00"))
                .setTaxAmount(new BigDecimal("14.95"))
                .setTotalAmount(TOTAL)
                .setCurrency("INR")
                .build();
    }

    private static OrderCancelled orderCancelled(long orderId, long customerId, String finalStatus, String cancelledBy,
                                                 String paymentStatus) {
        return OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setOrderId(orderId)
                .setCustomerId(customerId)
                .setRestaurantId(10L)
                .setFinalStatus(finalStatus)
                .setPreviousStatus("PLACED")
                .setCancelledBy(cancelledBy)
                .setReason("Kitchen closed early")
                .setPaymentMethod("UPI")
                .setPaymentStatus(paymentStatus)
                .setTotalAmount(TOTAL)
                .setCurrency("INR")
                .build();
    }

    /** An order only order-service knows (its OrderCreated event has not been consumed here). */
    private long newOrder(long customerId, String paymentMethod) {
        long orderId = randomId();
        orders.put(orderId, new OrderSummary(orderId, customerId, 10L, "PAYMENT_PENDING", paymentMethod, "PENDING",
                TOTAL, "INR"));
        return orderId;
    }

    private void setOrderStatus(long orderId, String status) {
        OrderSummary order = orders.get(orderId);
        orders.put(orderId, new OrderSummary(order.id(), order.customerId(), order.restaurantId(), status,
                order.paymentMethod(), order.paymentStatus(), order.totalAmount(), order.currency()));
    }

    private ResultActions pay(long customer, String key, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/payments")
                .with(TestTokens.as(customer, "CUSTOMER")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions refund(long paymentId, String key, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/payments/{p}/refunds", paymentId)
                .with(TestTokens.as(1, "PLATFORM_ADMIN")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(TestTokens.as(1, "PLATFORM_ADMIN")));
    }

    private static String upiBody(long orderId, String upiId) {
        return "{\"orderId\": " + orderId + ", \"paymentMethod\": \"UPI\", \"upiId\": \"" + upiId + "\"}";
    }

    private static String cardBody(long orderId, String number) {
        return "{\"orderId\": " + orderId + ", \"paymentMethod\": \"CARD\", \"card\": {\"number\": \"" + number
                + "\", \"expiryMonth\": 12, \"expiryYear\": 2030}}";
    }

    private static String walletBody(long orderId) {
        return "{\"orderId\": " + orderId + ", \"paymentMethod\": \"WALLET\", \"walletId\": \"wallet-98450\"}";
    }

    private static String result(Future<String> future) throws InterruptedException, ExecutionException,
            java.util.concurrent.TimeoutException {
        return future.get(30, TimeUnit.SECONDS);
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    }
}
