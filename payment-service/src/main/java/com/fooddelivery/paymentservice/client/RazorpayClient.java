package com.fooddelivery.paymentservice.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fooddelivery.paymentservice.config.AppProperties;
import com.fooddelivery.paymentservice.exception.PaymentGatewayException;
import com.fooddelivery.paymentservice.exception.RemoteServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Razorpay's REST API (https://razorpay.com/docs/api/): the four calls this service needs, with HTTP Basic auth
 * (key id + key secret) and timeouts. Amounts are in the smallest unit of the currency (paise: ₹353.95 = 35395).
 * <p>
 * Plain REST with Spring's RestClient, like {@link OrderServiceClient}, instead of the razorpay-java SDK (see the
 * note in pom.xml). Errors are translated: Razorpay refused the request -> {@link PaymentGatewayException} (with
 * Razorpay's explanation); Razorpay could not be reached -> {@link RemoteServiceException}.
 * <p>
 * Signatures are checked in {@link RazorpaySignatures} (no network involved).
 */
@Slf4j
@Component
public class RazorpayClient {

    static final String SERVICE = "razorpay";
    private static final String AMOUNT = "amount";

    private final RestClient restClient;
    private final AppProperties.Payment settings;

    @Autowired
    public RazorpayClient(RestClient.Builder builder, AppProperties appProperties) {
        this(createRestClient(builder, appProperties.payment().razorpay()), appProperties.payment());
    }

    /** For tests: a RestClient bound to a fake Razorpay. */
    RazorpayClient(RestClient restClient, AppProperties.Payment settings) {
        this.settings = settings;
        if (!settings.razorpayEnabled()) {
            this.restClient = restClient;
            return;
        }
        AppProperties.Razorpay razorpay = settings.razorpay();
        if (isBlank(razorpay.keyId()) || isBlank(razorpay.keySecret())) {
            // Fail at startup, not at the first customer's payment
            throw new IllegalStateException("PAYMENT_PROVIDER is RAZORPAY but RAZORPAY_KEY_ID or RAZORPAY_KEY_SECRET "
                    + "is empty: add the keys from the Razorpay Dashboard (Account & Settings -> API Keys) to .env");
        }
        // Every call is signed in with the key id and secret (HTTP Basic auth, over HTTPS)
        this.restClient = restClient.mutate()
                .defaultHeaders(headers -> headers.setBasicAuth(razorpay.keyId(), razorpay.keySecret()))
                .build();
        log.info("Razorpay payments are on. keyId={} mode={}", razorpay.keyId(),
                razorpay.keyId().startsWith("rzp_test_") ? "TEST (no real money)" : "LIVE");
    }

    /** A Razorpay order: what the customer pays in Checkout. status: created, attempted or paid. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RazorpayOrder(String id, long amount, String currency, String receipt, String status) {
    }

    /**
     * One attempt to pay a Razorpay order. status: created, authorized (the bank approved, the money is held),
     * captured (the money is taken: the payment is complete), refunded or failed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RazorpayPayment(String id,
                                  @JsonProperty("order_id") String orderId,
                                  String status,
                                  long amount,
                                  String currency,
                                  String method,
                                  String vpa,
                                  String wallet,
                                  String bank,
                                  @JsonProperty("error_description") String errorDescription) {

        public boolean isCaptured() {
            return "captured".equals(status);
        }

        public boolean isAuthorized() {
            return "authorized".equals(status);
        }

        public boolean isFailed() {
            return "failed".equals(status);
        }
    }

    /** A refund: status pending (Razorpay is sending it) or processed (the money is back with the customer). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RazorpayRefund(String id, long amount, String status) {
    }

    /** Razorpay's error body: {"error": {"code": "BAD_REQUEST_ERROR", "description": "..."}} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(ErrorDetails error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorDetails(String code, String description) {
    }

    public boolean isEnabled() {
        return settings.razorpayEnabled();
    }

    /** The public key id: Checkout in the browser needs it (the secret never leaves this server). */
    public String keyId() {
        return settings.razorpay().keyId();
    }

    public String merchantName() {
        return settings.razorpay().merchantName();
    }

    /** The secret the payment signatures are made with. */
    public String keySecret() {
        return settings.razorpay().keySecret();
    }

    public String webhookSecret() {
        return settings.razorpay().webhookSecret();
    }

    /** ₹353.95 -> 35395 paise. Our amounts always have two decimals; anything else is a bug and fails loudly. */
    public static long toPaise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    /** POST /v1/orders: a Razorpay order for this amount; Checkout is opened with its id. */
    public RazorpayOrder createOrder(long amountInPaise, String currency, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AMOUNT, amountInPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        return call("create order", () -> client().post().uri("/v1/orders").body(body)
                .retrieve().body(RazorpayOrder.class));
    }

    /** GET /v1/payments/{id}: what Razorpay itself says about a payment (never trust the browser alone). */
    public RazorpayPayment fetchPayment(String paymentId) {
        return call("fetch payment", () -> client().get().uri("/v1/payments/{id}", paymentId)
                .retrieve().body(RazorpayPayment.class));
    }

    /** POST /v1/payments/{id}/capture: takes the money of an authorized payment (needed when auto-capture is off). */
    public RazorpayPayment capturePayment(String paymentId, long amountInPaise, String currency) {
        Map<String, Object> body = Map.of(AMOUNT, amountInPaise, "currency", currency);
        return call("capture payment", () -> client().post().uri("/v1/payments/{id}/capture", paymentId).body(body)
                .retrieve().body(RazorpayPayment.class));
    }

    /** POST /v1/payments/{id}/refund: gives back (part of) a captured payment. */
    public RazorpayRefund refundPayment(String paymentId, long amountInPaise) {
        Map<String, Object> body = Map.of(AMOUNT, amountInPaise);
        return call("refund payment", () -> client().post().uri("/v1/payments/{id}/refund", paymentId).body(body)
                .retrieve().body(RazorpayRefund.class));
    }

    private <T> T call(String what, Supplier<T> request) {
        try {
            T answer = request.get();
            if (answer == null) {
                throw new PaymentGatewayException("Razorpay sent an empty answer to " + what);
            }
            return answer;
        } catch (RestClientResponseException e) {
            String reason = reasonOf(e);
            log.warn("Razorpay refused to {}. status={} reason={}", what, e.getStatusCode().value(), reason);
            throw new PaymentGatewayException("Razorpay could not " + what + ": " + reason);
        } catch (ResourceAccessException e) {
            throw new RemoteServiceException(SERVICE, e);
        }
    }

    /** Razorpay's own description of the problem ("Authentication failed", "Payment has already been captured"). */
    @SuppressWarnings("checkstyle:IllegalCatch") // whatever the body is, the HTTP status remains as the reason
    private static String reasonOf(RestClientResponseException e) {
        try {
            ErrorResponse error = e.getResponseBodyAs(ErrorResponse.class);
            if (error != null && error.error() != null && !isBlank(error.error().description())) {
                return error.error().description();
            }
        } catch (RuntimeException ignored) {
            // Not Razorpay's JSON (a proxy's HTML page, for example): the HTTP status says enough
        }
        return e.getStatusText();
    }

    private RestClient client() {
        if (!isEnabled()) {
            throw new IllegalStateException("Razorpay is not switched on: set PAYMENT_PROVIDER=RAZORPAY and its keys");
        }
        return restClient;
    }

    private static RestClient createRestClient(RestClient.Builder builder, AppProperties.Razorpay razorpay) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(razorpay.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(razorpay.readTimeout());

        // No X-Request-Id interceptor (unlike OrderServiceClient): our internal ids are not Razorpay's business
        return builder.clone()
                .baseUrl(razorpay.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
