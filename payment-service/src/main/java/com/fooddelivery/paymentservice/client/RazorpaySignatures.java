package com.fooddelivery.paymentservice.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Razorpay's two signatures, as its documentation defines them. Both are HMAC-SHA256 in lowercase hex:
 * <ul>
 *   <li><b>payment signature</b>, returned by Checkout after a payment: HMAC of {@code "<order_id>|<payment_id>"}
 *       with the <b>key secret</b>. Only Razorpay and this server know the secret, so a valid signature proves the
 *       browser did not make up the payment ids;</li>
 *   <li><b>webhook signature</b>, in the X-Razorpay-Signature header: HMAC of the <b>raw request body</b> with the
 *       <b>webhook secret</b> (the one typed in when adding the webhook in the Dashboard).</li>
 * </ul>
 * Signatures are compared in constant time ({@link MessageDigest#isEqual}), so the time a comparison takes tells an
 * attacker nothing about the right value. An empty secret or signature is never valid.
 */
public final class RazorpaySignatures {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private RazorpaySignatures() {
    }

    /** The signature Checkout returns for this payment of this order. */
    public static String paymentSignature(String orderId, String paymentId, String keySecret) {
        return hmacSha256Hex(orderId + "|" + paymentId, keySecret);
    }

    public static boolean isValidPaymentSignature(String orderId, String paymentId, String signature,
                                                  String keySecret) {
        if (isBlank(orderId) || isBlank(paymentId) || isBlank(keySecret)) {
            return false;
        }
        return sameSignature(paymentSignature(orderId, paymentId, keySecret), signature);
    }

    /** The signature Razorpay sends with a webhook whose body is exactly {@code payload}. */
    public static String webhookSignature(String payload, String webhookSecret) {
        return hmacSha256Hex(payload, webhookSecret);
    }

    public static boolean isValidWebhookSignature(String payload, String signature, String webhookSecret) {
        if (payload == null || isBlank(webhookSecret)) {
            return false;
        }
        return sameSignature(webhookSignature(payload, webhookSecret), signature);
    }

    private static boolean sameSignature(String expected, String received) {
        if (isBlank(received)) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                received.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is part of every Java runtime: this cannot happen on a working JVM
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
