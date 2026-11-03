package com.fooddelivery.restaurantservice.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Restaurant lifecycle. Only the transitions listed here are allowed:
 *
 * <pre>
 *  Owner submits          Admin review
 *  ------------> PENDING ----+----> APPROVED ---> ACTIVE <---> INACTIVE
 *                   ^         |                    |  ^          |
 *                   |         v                    v  |          v
 *     owner resubmits <--- REJECTED              SUSPENDED <------+
 * </pre>
 */
public enum RestaurantStatus {
    PENDING,
    APPROVED,
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    REJECTED;

    private static final Map<RestaurantStatus, Set<RestaurantStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(RestaurantStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(APPROVED, REJECTED));
        ALLOWED_TRANSITIONS.put(REJECTED, EnumSet.of(PENDING));
        ALLOWED_TRANSITIONS.put(APPROVED, EnumSet.of(ACTIVE));
        ALLOWED_TRANSITIONS.put(ACTIVE, EnumSet.of(INACTIVE, SUSPENDED));
        ALLOWED_TRANSITIONS.put(INACTIVE, EnumSet.of(ACTIVE, SUSPENDED));
        ALLOWED_TRANSITIONS.put(SUSPENDED, EnumSet.of(ACTIVE));
    }

    public boolean canChangeTo(RestaurantStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }
}
