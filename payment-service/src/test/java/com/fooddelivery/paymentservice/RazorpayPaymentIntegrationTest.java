package com.fooddelivery.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.paymentservice.client.OrderServiceClient;
import com.fooddelivery.paymentservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.paymentservice.client.RazorpayClient;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayOrder;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayPayment;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayRefund;
import com.fooddelivery.paymentservice.client.RazorpaySignatures;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import com.fooddelivery.paymentservice.exception.RemoteServiceException;
import com.fooddelivery.paymentservice.repository.OutboxEventRepository;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Razorpay Checkout end to end (PAYMENT_PROVIDER=RAZORPAY) against a real MySQL and Kafka. Only Razorpay's HTTP API
 * is faked (the client's network calls); signatures are real HMACs made with the test secrets, exactly as Razorpay
 * makes them.
 */
@SpringBootTest(properties = {
        "app.payment.provider=RAZORPAY",
        "app.payment.razorpay.key-id=" + RazorpayPaymentIntegrationTest.KEY_ID,
        "app.payment.razorpay.key-secret=" + RazorpayPaymentIntegrationTest.KEY_SECRET,
        "app.payment.razorpay.webhook-secret=" + RazorpayPaymentIntegrationTest.WEBHOOK_SECRET})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// Its own context (Razorpay settings), closed afterwards so only one MySQL + Kafka pair runs at a time
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RazorpayPaymentIntegrationTest {

    static final String KEY_ID = "rzp_test_IntegrationKey";
    static final String KEY_SECRET = "integration-key-secret";
    static final String WEBHOOK_SECRET = "integration-webhook-secret";
    private static final BigDecimal TOTAL = new BigDecimal("353.95");
    private static final long TOTAL_PAISE = 35395;

    private final Map<Long, OrderSummary> orders = new ConcurrentHashMap<>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @MockitoBean
    private OrderServiceClient orderServiceClient;
    /** The real client (real settings), with its calls to Razorpay's API answered by the tests. */
    @MockitoSpyBean
    private RazorpayClient razorpayClient;

    @BeforeEach
    void fakeOrderServiceAndRazorpay() {
        when(orderServiceClient.findOrder(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(orders.get(invocation.<Long>getArgument(0))));
        // Razorpay creates an order for exactly what it was asked
        doAnswer(invocation -> new RazorpayOrder(razorpayId("order_"), invocation.getArgument(0),
                invocation.getArgument(1), invocation.getArgument(2), "created"))
                .when(razorpayClient).createOrder(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void theBrowserGetsThePublicKeyIdButNeverTheSecret() throws Exception {
        String body = asCustomer(randomId(), get("/api/v1/payments/razorpay/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.keyId").value(KEY_ID))
                .andExpect(jsonPath("$.testMode").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(KEY_SECRET).doesNotContain(WEBHOOK_SECRET);
    }

    @Test
    void verifiedPaymentIsCapturedAndOrderServiceIsToldOnce() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");

        String checkout = startCheckout(customer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value(KEY_ID))
                .andExpect(jsonPath("$.amount").value(TOTAL_PAISE))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.preferredMethod").value("upi"))
                .andExpect(jsonPath("$.description").value("Order #" + orderId))
                .andReturn().getResponse().getContentAsString();
        assertThat(checkout).doesNotContain(KEY_SECRET);
        String razorpayOrderId = JsonPath.read(checkout, "$.razorpayOrderId");
        long paymentId = ((Number) JsonPath.read(checkout, "$.paymentId")).longValue();

        // Opening Checkout again (reload, second tab) reuses the same Razorpay order: one order, one charge
        startCheckout(customer, orderId)
                .andExpect(jsonPath("$.razorpayOrderId").value(razorpayOrderId))
                .andExpect(jsonPath("$.paymentId").value(paymentId));
        verify(razorpayClient, times(1)).createOrder(anyLong(), anyString(), anyString(), anyMap());
        Payment open = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(open.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(open.getProvider()).isEqualTo(PaymentProvider.RAZORPAY);

        // The simulated gateway cannot be used to skip Razorpay
        mockMvc.perform(post("/api/v1/payments")
                        .with(TestTokens.as(customer, "CUSTOMER"))
                        .header("Idempotency-Key", "pay-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": " + orderId + ", \"paymentMethod\": \"UPI\", \"upiId\": \"a@okhdfc\"}"))
                .andExpect(status().isUnprocessableContent());

        // Checkout reports success; Razorpay says the money is held (authorized): it is captured, then SUCCESS
        String razorpayPaymentId = razorpayId("pay_");
        doReturn(razorpayPayment(razorpayPaymentId, razorpayOrderId, "authorized", TOTAL_PAISE, null))
                .when(razorpayClient).fetchPayment(razorpayPaymentId);
        doReturn(razorpayPayment(razorpayPaymentId, razorpayOrderId, "captured", TOTAL_PAISE, null))
                .when(razorpayClient).capturePayment(razorpayPaymentId, TOTAL_PAISE, "INR");

        verifyPayment(customer, razorpayOrderId, razorpayPaymentId, signatureOf(razorpayOrderId, razorpayPaymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.provider").value("RAZORPAY"))
                .andExpect(jsonPath("$.providerReference").value(razorpayPaymentId))
                .andExpect(jsonPath("$.providerOrderId").value(razorpayOrderId))
                .andExpect(jsonPath("$.instrument").value("UPI me***@okhdfc"));

        PaymentStatusChanged event = KafkaTestEvents.awaitEvent(consumerFactory, Topics.PAYMENT_STATUS_CHANGED,
                String.valueOf(orderId), PaymentStatusChanged.class);
        assertThat(event.getPaymentId()).isEqualTo(paymentId);
        assertThat(event.getStatus()).isEqualTo("SUCCESS");
        assertThat(event.getAmount()).isEqualByComparingTo(TOTAL);

        // The same confirmation again (double click, retry): same answer, no second capture, no second event
        verifyPayment(customer, razorpayOrderId, razorpayPaymentId, signatureOf(razorpayOrderId, razorpayPaymentId))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        verify(razorpayClient, times(1)).capturePayment(anyString(), anyLong(), anyString());
        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("PAYMENT", paymentId)).hasSize(1);
    }

    @Test
    void aForgedSignatureIsRefusedAndChangesNothing() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "CARD");
        String razorpayOrderId = razorpayOrderOf(startCheckout(customer, orderId));
        String razorpayPaymentId = razorpayId("pay_");

        // "Paid!" says the browser, but the signature was not made with our key secret
        String forged = RazorpaySignatures.paymentSignature(razorpayOrderId, razorpayPaymentId, "guessed-secret");
        verifyPayment(customer, razorpayOrderId, razorpayPaymentId, forged)
                .andExpect(status().isUnprocessableContent());

        verify(razorpayClient, never()).fetchPayment(anyString());
        assertThat(paymentOf(razorpayOrderId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void aPaymentForAnotherAmountIsNeverAccepted() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        String razorpayOrderId = razorpayOrderOf(startCheckout(customer, orderId));
        String razorpayPaymentId = razorpayId("pay_");
        doReturn(razorpayPayment(razorpayPaymentId, razorpayOrderId, "captured", 100, null))
                .when(razorpayClient).fetchPayment(razorpayPaymentId);

        verifyPayment(customer, razorpayOrderId, razorpayPaymentId, signatureOf(razorpayOrderId, razorpayPaymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        assertThat(paymentOf(razorpayOrderId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void theWebhookCompletesThePaymentOnceEvenWhenRazorpaySendsItAgain() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        String razorpayOrderId = razorpayOrderOf(startCheckout(customer, orderId));
        String razorpayPaymentId = razorpayId("pay_");
        String captured = paymentEvent("payment.captured", razorpayOrderId, razorpayPaymentId, "captured", null);
        String eventId = razorpayId("evt_");

        // The customer closed the browser: only Razorpay's webhook tells us (no X-User headers)
        webhook(captured, webhookSignatureOf(captured), eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"));
        Payment paid = paymentOf(razorpayOrderId);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paid.getProviderReference()).isEqualTo(razorpayPaymentId);

        // Razorpay retries the same event, then sends order.paid about the same payment
        webhook(captured, webhookSignatureOf(captured), eventId)
                .andExpect(jsonPath("$.result").value("DUPLICATE"));
        String orderPaid = paymentEvent("order.paid", razorpayOrderId, razorpayPaymentId, "captured", null);
        webhook(orderPaid, webhookSignatureOf(orderPaid), razorpayId("evt_"))
                .andExpect(jsonPath("$.result").value("PROCESSED"));

        // The browser's late confirmation finds it paid
        verifyPayment(customer, razorpayOrderId, razorpayPaymentId, signatureOf(razorpayOrderId, razorpayPaymentId))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        verify(razorpayClient, never()).fetchPayment(anyString());
        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("PAYMENT", paid.getId()))
                .hasSize(1);
        assertThat(KafkaTestEvents.awaitEvent(consumerFactory, Topics.PAYMENT_STATUS_CHANGED, String.valueOf(orderId),
                PaymentStatusChanged.class).getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void aWebhookWithAWrongSignatureIsRefused() throws Exception {
        long customer = randomId();
        String razorpayOrderId = razorpayOrderOf(startCheckout(customer, newOrder(customer, "UPI")));
        String captured = paymentEvent("payment.captured", razorpayOrderId, razorpayId("pay_"), "captured", null);

        webhook(captured, RazorpaySignatures.webhookSignature(captured, "not-the-webhook-secret"), razorpayId("evt_"))
                .andExpect(status().isUnauthorized());
        webhook(captured, null, razorpayId("evt_"))
                .andExpect(status().isUnauthorized());

        assertThat(paymentOf(razorpayOrderId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void aFailedAttemptKeepsThePaymentOpenForAnotherTry() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "CARD");
        String razorpayOrderId = razorpayOrderOf(startCheckout(customer, orderId));
        String failed = paymentEvent("payment.failed", razorpayOrderId, razorpayId("pay_"), "failed",
                "Payment was declined by the bank");

        webhook(failed, webhookSignatureOf(failed), razorpayId("evt_"))
                .andExpect(jsonPath("$.result").value("PROCESSED"));

        Payment open = paymentOf(razorpayOrderId);
        assertThat(open.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(open.getFailureReason()).isEqualTo("Razorpay: Payment was declined by the bank");
        // The customer tries again in Checkout, on the same Razorpay order
        startCheckout(customer, orderId).andExpect(jsonPath("$.razorpayOrderId").value(razorpayOrderId));
    }

    @Test
    void razorpayOutOfReachFailsTheAttemptAndTheCustomerCanStartAgain() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "WALLET");
        doThrow(new RemoteServiceException("razorpay", new IOException("Connection refused")))
                .when(razorpayClient).createOrder(anyLong(), anyString(), anyString(), anyMap());

        startCheckout(customer, orderId).andExpect(status().isServiceUnavailable());
        assertThat(paymentRepository.findByCustomerIdAndOrderIdOrderByCreatedAtDesc(customer, orderId))
                .singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED));

        fakeOrderServiceAndRazorpay(); // Razorpay is back
        startCheckout(customer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razorpayOrderId").exists());
    }

    @Test
    void aRazorpayPaymentIsRefundedThroughRazorpay() throws Exception {
        long customer = randomId();
        long orderId = newOrder(customer, "UPI");
        String razorpayOrderId = razorpayOrderOf(startCheckout(customer, orderId));
        String razorpayPaymentId = razorpayId("pay_");
        String captured = paymentEvent("payment.captured", razorpayOrderId, razorpayPaymentId, "captured", null);
        webhook(captured, webhookSignatureOf(captured), razorpayId("evt_")).andExpect(status().isOk());
        long paymentId = paymentOf(razorpayOrderId).getId();

        setOrderStatus(orderId, "CANCELLED");
        doReturn(new RazorpayRefund("rfnd_Test1", TOTAL_PAISE, "processed"))
                .when(razorpayClient).refundPayment(razorpayPaymentId, TOTAL_PAISE);

        mockMvc.perform(post("/api/v1/admin/payments/{p}/refunds", paymentId)
                        .with(TestTokens.as(1, "PLATFORM_ADMIN"))
                        .header("Idempotency-Key", "refund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Restaurant closed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.providerReference").value("rfnd_Test1"));

        verify(razorpayClient).refundPayment(razorpayPaymentId, TOTAL_PAISE);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    private ResultActions startCheckout(long customer, long orderId) throws Exception {
        return asCustomer(customer, post("/api/v1/payments/razorpay/orders")
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\": " + orderId + "}"));
    }

    private ResultActions verifyPayment(long customer, String razorpayOrderId, String razorpayPaymentId,
                                        String signature) throws Exception {
        return asCustomer(customer, post("/api/v1/payments/razorpay/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpayOrderId\": \"" + razorpayOrderId + "\", \"razorpayPaymentId\": \""
                        + razorpayPaymentId + "\", \"razorpaySignature\": \"" + signature + "\"}"));
    }

    private ResultActions webhook(String payload, String signature, String eventId) throws Exception {
        var request = post("/api/v1/payments/razorpay/webhook")
                .contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("X-Razorpay-Event-Id", eventId);
        if (signature != null) {
            request.header("X-Razorpay-Signature", signature);
        }
        return mockMvc.perform(request);
    }

    private ResultActions asCustomer(long customer, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(TestTokens.as(customer, "CUSTOMER")));
    }

    private Payment paymentOf(String razorpayOrderId) {
        return paymentRepository.findByProviderOrderId(razorpayOrderId).orElseThrow();
    }

    private static String razorpayOrderOf(ResultActions checkout) throws Exception {
        return JsonPath.read(checkout.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.razorpayOrderId");
    }

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

    private static RazorpayPayment razorpayPayment(String id, String orderId, String status, long amount,
                                                   String errorDescription) {
        return new RazorpayPayment(id, orderId, status, amount, "INR", "upi", "meera@okhdfc", null, null,
                errorDescription);
    }

    /** A webhook body shaped like Razorpay's (with fields this service does not read). */
    private static String paymentEvent(String event, String razorpayOrderId, String razorpayPaymentId, String status,
                                       String errorDescription) {
        return """
                {"entity": "event", "account_id": "acc_Test", "event": "%s", "contains": ["payment"],
                 "payload": {"payment": {"entity": {"id": "%s", "entity": "payment", "amount": %d, "currency": "INR",
                   "status": "%s", "order_id": "%s", "method": "upi", "vpa": "meera@okhdfc", "captured": %b,
                   "error_description": %s}}},
                 "created_at": 1767225600}
                """.formatted(event, razorpayPaymentId, TOTAL_PAISE, status, razorpayOrderId,
                "captured".equals(status), errorDescription == null ? "null" : "\"" + errorDescription + "\"");
    }

    private static String signatureOf(String razorpayOrderId, String razorpayPaymentId) {
        return RazorpaySignatures.paymentSignature(razorpayOrderId, razorpayPaymentId, KEY_SECRET);
    }

    private static String webhookSignatureOf(String payload) {
        return RazorpaySignatures.webhookSignature(payload, WEBHOOK_SECRET);
    }

    /** "order_", "pay_" or "evt_" + 14 letters and digits, like Razorpay's ids. */
    private static String razorpayId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    }
}
