package com.fooddelivery.orderservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fooddelivery.orderservice.dto.request.PlaceOrderRequest;

/**
 * A short, stable hash of a checkout request. If a client reuses an Idempotency-Key with a
 * *different* request, the hashes differ and the request is rejected instead of silently
 * returning an unrelated order.
 */
public final class IdempotencyFingerprint {

    private IdempotencyFingerprint() {
    }

    public static String of(PlaceOrderRequest request) {
        String canonical = request.addressId() + "|" + request.paymentMethod() + "|"
                + (request.notes() == null ? "" : request.notes().trim());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
