package com.fooddelivery.orderservice.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. Only the transitions listed here are allowed (no "DELIVERED -> PREPARING").
 *
 * <pre>
 * PAYMENT_PENDING --(paid, Phase 6)--> PLACED --(restaurant)--> ACCEPTED --> PREPARING --> READY_FOR_PICKUP
 *       |                                 |   \--(restaurant)--> REJECTED                         |
 *       v                                 v                                                       v (Phase 10)
 *   CANCELLED                         CANCELLED                                 OUT_FOR_DELIVERY --> DELIVERED
 * </pre>
 * Cash-on-delivery orders start directly as PLACED. Admins may cancel any order that is not finished yet.
 */
public enum OrderStatus {
    /** Online payment not completed yet (connected in Phase 6). */
    PAYMENT_PENDING,
    /** Paid or cash on delivery: waiting for the restaurant. */
    PLACED,
    ACCEPTED,
    PREPARING,
    READY_FOR_PICKUP,
    /** Picked up by a delivery partner (Phase 10). */
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    REJECTED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PAYMENT_PENDING, EnumSet.of(PLACED, CANCELLED));
        ALLOWED_TRANSITIONS.put(PLACED, EnumSet.of(ACCEPTED, REJECTED, CANCELLED));
        ALLOWED_TRANSITIONS.put(ACCEPTED, EnumSet.of(PREPARING, CANCELLED));
        ALLOWED_TRANSITIONS.put(PREPARING, EnumSet.of(READY_FOR_PICKUP, CANCELLED));
        ALLOWED_TRANSITIONS.put(READY_FOR_PICKUP, EnumSet.of(OUT_FOR_DELIVERY, CANCELLED));
        ALLOWED_TRANSITIONS.put(OUT_FOR_DELIVERY, EnumSet.of(DELIVERED));
        ALLOWED_TRANSITIONS.put(DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(REJECTED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canChangeTo(OrderStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }

    /** Customers may cancel only before the restaurant has accepted the order. */
    public boolean isCancellableByCustomer() {
        return this == PAYMENT_PENDING || this == PLACED;
    }

    /** The order ended without delivery. */
    public boolean isCancelledOrRejected() {
        return this == CANCELLED || this == REJECTED;
    }

    /** Not finished yet: neither delivered nor cancelled/rejected. */
    public boolean isInProgress() {
        return this != DELIVERED && !isCancelledOrRejected();
    }

    public boolean isAcceptedByRestaurant() {
        return this == ACCEPTED || this == PREPARING || this == READY_FOR_PICKUP
                || this == OUT_FOR_DELIVERY || this == DELIVERED;
    }
}
