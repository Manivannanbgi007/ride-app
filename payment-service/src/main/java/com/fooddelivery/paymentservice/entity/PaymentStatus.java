package com.fooddelivery.paymentservice.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment lifecycle: INITIATED -> PROCESSING -> SUCCESS or FAILED; SUCCESS -> REFUNDED (fully refunded).
 * A partly refunded payment stays SUCCESS with refundedAmount > 0.
 */
public enum PaymentStatus {
    INITIATED,
    PROCESSING,
    SUCCESS,
    FAILED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(INITIATED, EnumSet.of(PROCESSING, FAILED));
        ALLOWED_TRANSITIONS.put(PROCESSING, EnumSet.of(SUCCESS, FAILED));
        ALLOWED_TRANSITIONS.put(SUCCESS, EnumSet.of(REFUNDED));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    public boolean canChangeTo(PaymentStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }
}
