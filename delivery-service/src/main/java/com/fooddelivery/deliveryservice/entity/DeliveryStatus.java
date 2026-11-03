package com.fooddelivery.deliveryservice.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Delivery lifecycle. Only the transitions listed here are allowed.
 *
 * <pre>
 * PENDING --(assignment)--> ASSIGNED --(partner accepts)--> ACCEPTED --> PICKED_UP --> DELIVERED
 *    ^                         |                               |
 *    +---(declined, no answer, +----(admin reassigns)----------+
 *         admin unassigns)
 * </pre>
 * Any delivery that is not finished becomes CANCELLED when its order is cancelled.
 */
public enum DeliveryStatus {
    /** The food is ready; waiting for a delivery partner. */
    PENDING,
    /** Offered to a partner, who has not accepted yet. */
    ASSIGNED,
    /** The partner is on the way to the restaurant. */
    ACCEPTED,
    /** The partner has the food and is on the way to the customer. */
    PICKED_UP,
    DELIVERED,
    CANCELLED;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(DeliveryStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(ASSIGNED, CANCELLED));
        // ASSIGNED -> ASSIGNED: an admin hands the delivery to another partner
        ALLOWED_TRANSITIONS.put(ASSIGNED, EnumSet.of(ACCEPTED, ASSIGNED, PENDING, CANCELLED));
        ALLOWED_TRANSITIONS.put(ACCEPTED, EnumSet.of(PICKED_UP, ASSIGNED, PENDING, CANCELLED));
        // Cancelled after pickup only when the order was cancelled at the same moment (the food goes back)
        ALLOWED_TRANSITIONS.put(PICKED_UP, EnumSet.of(DELIVERED, CANCELLED));
        ALLOWED_TRANSITIONS.put(DELIVERED, EnumSet.noneOf(DeliveryStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(DeliveryStatus.class));
    }

    public boolean canChangeTo(DeliveryStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }

    /** A partner is working on it. */
    public boolean hasPartner() {
        return this == ASSIGNED || this == ACCEPTED || this == PICKED_UP;
    }

    /** Before pickup, another partner can still take over. */
    public boolean isReassignable() {
        return this == PENDING || this == ASSIGNED || this == ACCEPTED;
    }

    public boolean isFinished() {
        return this == DELIVERED || this == CANCELLED;
    }
}
