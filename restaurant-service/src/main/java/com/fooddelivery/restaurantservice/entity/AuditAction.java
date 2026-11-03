package com.fooddelivery.restaurantservice.entity;

public enum AuditAction {
    RESTAURANT_APPROVED,
    RESTAURANT_REJECTED,
    RESTAURANT_ACTIVATED,
    RESTAURANT_DEACTIVATED,
    RESTAURANT_SUSPENDED,
    /** All restaurants published again as events (for example to rebuild the search index). */
    RESTAURANT_EVENTS_REPUBLISHED
}
