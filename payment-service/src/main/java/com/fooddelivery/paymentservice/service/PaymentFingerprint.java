package com.fooddelivery.paymentservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fooddelivery.paymentservice.dto.request.PayRequest;

/**
 * Hash of the parts of a payment request that define "the same payment": order and method.
 * Card numbers are deliberately not part of it (they must not be stored, not even hashed).
 */
public final class PaymentFingerprint {

    private PaymentFingerprint() {
    }

    public static String of(PayRequest request) {
        String canonical = request.orderId() + "|" + request.paymentMethod();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
