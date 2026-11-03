package com.fooddelivery.paymentservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Razorpay's HMAC-SHA256 signatures: the only proof that a payment or webhook really comes from Razorpay. */
class RazorpaySignaturesTest {

    private static final String KEY_SECRET = "test-key-secret";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Test
    void webhookSignatureIsTheHmacSha256OfTheBodyInHex() {
        // RFC 4231, test case 2: a published HMAC-SHA256 test vector
        assertThat(RazorpaySignatures.webhookSignature("what do ya want for nothing?", "Jefe"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void paymentSignatureSignsOrderIdPipePaymentIdWithTheKeySecret() {
        assertThat(RazorpaySignatures.paymentSignature("order_A1", "pay_B2", KEY_SECRET))
                .isEqualTo(RazorpaySignatures.webhookSignature("order_A1|pay_B2", KEY_SECRET));
    }

    @Test
    void onlyTheExactPaymentSignatureIsAccepted() {
        String signature = RazorpaySignatures.paymentSignature("order_A1", "pay_B2", KEY_SECRET);

        assertThat(RazorpaySignatures.isValidPaymentSignature("order_A1", "pay_B2", signature, KEY_SECRET)).isTrue();
        // Another payment, another order, another secret, or nothing at all: refused
        assertThat(RazorpaySignatures.isValidPaymentSignature("order_A1", "pay_OTHER", signature, KEY_SECRET)).isFalse();
        assertThat(RazorpaySignatures.isValidPaymentSignature("order_OTHER", "pay_B2", signature, KEY_SECRET)).isFalse();
        assertThat(RazorpaySignatures.isValidPaymentSignature("order_A1", "pay_B2", signature, "other")).isFalse();
        assertThat(RazorpaySignatures.isValidPaymentSignature("order_A1", "pay_B2", null, KEY_SECRET)).isFalse();
        assertThat(RazorpaySignatures.isValidPaymentSignature("order_A1", "pay_B2", " ", KEY_SECRET)).isFalse();
        // Without a configured secret nothing can be verified, so nothing is accepted
        assertThat(RazorpaySignatures.isValidPaymentSignature("order_A1", "pay_B2", signature, "")).isFalse();
    }

    @Test
    void aWebhookIsAcceptedOnlyForTheExactBody() {
        String body = "{\"event\":\"payment.captured\"}";
        String signature = RazorpaySignatures.webhookSignature(body, WEBHOOK_SECRET);

        assertThat(RazorpaySignatures.isValidWebhookSignature(body, signature, WEBHOOK_SECRET)).isTrue();
        // One changed character (or even reformatted JSON) breaks the signature
        assertThat(RazorpaySignatures.isValidWebhookSignature("{\"event\": \"payment.captured\"}", signature,
                WEBHOOK_SECRET)).isFalse();
        assertThat(RazorpaySignatures.isValidWebhookSignature(body, signature, "other-secret")).isFalse();
        assertThat(RazorpaySignatures.isValidWebhookSignature(body, null, WEBHOOK_SECRET)).isFalse();
        assertThat(RazorpaySignatures.isValidWebhookSignature(body, signature, null)).isFalse();
    }
}
