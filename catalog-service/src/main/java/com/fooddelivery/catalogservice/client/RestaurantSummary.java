package com.fooddelivery.catalogservice.client;

/**
 * Restaurant data returned by restaurant-service's internal API (or read from the local copy).
 * Status is a plain string ("tolerant reader"): new statuses there must not break this service.
 *
 * @param version the restaurant's version in restaurant-service; null from older restaurant-service versions
 */
public record RestaurantSummary(
        Long id,
        Long ownerUserId,
        String name,
        String status,
        Long version
) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isSuspended() {
        return "SUSPENDED".equals(status);
    }
}
