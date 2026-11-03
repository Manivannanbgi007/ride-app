package com.fooddelivery.paymentservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayOrder;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayPayment;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayRefund;
import com.fooddelivery.paymentservice.config.AppProperties;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
import com.fooddelivery.paymentservice.exception.PaymentGatewayException;
import com.fooddelivery.paymentservice.exception.RemoteServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Razorpay REST calls against a fake Razorpay (no network): auth, request bodies, answers and errors. */
class RazorpayClientTest {

    private static final String API = "https://api.razorpay.com";
    private static final AppProperties.Razorpay KEYS = new AppProperties.Razorpay("rzp_test_Key123", "secret456",
            "webhook789", API, "Front-App", Duration.ofSeconds(3), Duration.ofSeconds(10));

    private MockRestServiceServer razorpay;
    private RazorpayClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        razorpay = MockRestServiceServer.bindTo(builder).build();
        client = new RazorpayClient(builder.build(), new AppProperties.Payment(PaymentProvider.RAZORPAY, KEYS));
    }

    @Test
    void createsAnOrderSignedInWithTheKeysAndTheAmountInPaise() {
        String basicAuth = Base64.getEncoder().encodeToString("rzp_test_Key123:secret456".getBytes(StandardCharsets.UTF_8));
        razorpay.expect(requestTo(API + "/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth))
                .andExpect(jsonPath("$.amount").value(35395))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("order-42-payment-7"))
                .andExpect(jsonPath("$.notes.orderId").value("42"))
                .andRespond(withSuccess("""
                        {"id": "order_Q1", "entity": "order", "amount": 35395, "amount_paid": 0, "currency": "INR",
                         "receipt": "order-42-payment-7", "status": "created", "attempts": 0}
                        """, MediaType.APPLICATION_JSON));

        RazorpayOrder order = client.createOrder(35395, "INR", "order-42-payment-7", Map.of("orderId", "42"));

        assertThat(order).isEqualTo(new RazorpayOrder("order_Q1", 35395, "INR", "order-42-payment-7", "created"));
        razorpay.verify();
    }

    @Test
    void readsAPaymentWithItsOrderStatusAndFailureReason() {
        razorpay.expect(requestTo(API + "/v1/payments/pay_P1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": "pay_P1", "entity": "payment", "amount": 35395, "currency": "INR", "status": "failed",
                         "order_id": "order_Q1", "method": "upi", "vpa": "meera@okhdfc", "captured": false,
                         "error_code": "BAD_REQUEST_ERROR", "error_description": "Payment was declined by the bank"}
                        """, MediaType.APPLICATION_JSON));

        RazorpayPayment payment = client.fetchPayment("pay_P1");

        assertThat(payment.orderId()).isEqualTo("order_Q1");
        assertThat(payment.amount()).isEqualTo(35395);
        assertThat(payment.isFailed()).isTrue();
        assertThat(payment.isCaptured()).isFalse();
        assertThat(payment.errorDescription()).isEqualTo("Payment was declined by the bank");
    }

    @Test
    void capturesAndRefundsWithTheAmountInPaise() {
        razorpay.expect(requestTo(API + "/v1/payments/pay_P1/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(35395))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andRespond(withSuccess("""
                        {"id": "pay_P1", "amount": 35395, "currency": "INR", "status": "captured", "order_id": "order_Q1"}
                        """, MediaType.APPLICATION_JSON));
        razorpay.expect(requestTo(API + "/v1/payments/pay_P1/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(10000))
                .andRespond(withSuccess("""
                        {"id": "rfnd_R1", "entity": "refund", "amount": 10000, "payment_id": "pay_P1", "status": "processed"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.capturePayment("pay_P1", 35395, "INR").isCaptured()).isTrue();
        assertThat(client.refundPayment("pay_P1", 10000)).isEqualTo(new RazorpayRefund("rfnd_R1", 10000, "processed"));
        razorpay.verify();
    }

    @Test
    void razorpaysRefusalCarriesItsOwnExplanation() {
        razorpay.expect(requestTo(API + "/v1/payments/pay_P1/capture"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": {"code": "BAD_REQUEST_ERROR", "description": "This payment has already been captured"}}
                        """));

        assertThatThrownBy(() -> client.capturePayment("pay_P1", 35395, "INR"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("This payment has already been captured");
    }

    @Test
    void anErrorPageWithoutRazorpaysJsonStillFailsClearly() {
        razorpay.expect(requestTo(API + "/v1/payments/pay_P1"))
                .andRespond(withServerError().contentType(MediaType.TEXT_HTML).body("<html>Bad gateway</html>"));

        assertThatThrownBy(() -> client.fetchPayment("pay_P1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Razorpay could not fetch payment");
    }

    @Test
    void razorpayOutOfReachIsARemoteServiceProblem() {
        razorpay.expect(requestTo(API + "/v1/payments/pay_P1"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.fetchPayment("pay_P1")).isInstanceOf(RemoteServiceException.class);
    }

    @Test
    void razorpayOnWithoutKeysStopsTheServiceAtStartup() {
        AppProperties.Razorpay noKeys = new AppProperties.Razorpay("", "", "", API, "Front-App",
                Duration.ofSeconds(3), Duration.ofSeconds(10));
        AppProperties.Payment settings = new AppProperties.Payment(PaymentProvider.RAZORPAY, noKeys);
        RestClient restClient = RestClient.create();

        assertThatThrownBy(() -> new RazorpayClient(restClient, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZORPAY_KEY_ID or RAZORPAY_KEY_SECRET");
    }

    @Test
    void withTheSimulatedGatewayRazorpayIsNeverCalled() {
        RazorpayClient off = new RazorpayClient(RestClient.create(),
                new AppProperties.Payment(PaymentProvider.SIMULATED, KEYS));

        assertThat(off.isEnabled()).isFalse();
        assertThatThrownBy(() -> off.fetchPayment("pay_P1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void amountsBecomePaise() {
        assertThat(RazorpayClient.toPaise(new BigDecimal("353.95"))).isEqualTo(35395);
        assertThat(RazorpayClient.toPaise(new BigDecimal("10"))).isEqualTo(1000);
        assertThat(RazorpayClient.toPaise(new BigDecimal("0.10"))).isEqualTo(10);
    }

    @Test
    void settingsNeverPrintTheSecrets() {
        assertThat(KEYS.toString()).contains("rzp_test_Key123").doesNotContain("secret456").doesNotContain("webhook789");
    }
}
