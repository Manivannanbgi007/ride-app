package com.fooddelivery.restaurantservice.client;

/**
 * User data returned by user-service's internal API.
 * Role and status are plain strings on purpose: this service should not break
 * when user-service adds a new role or status ("tolerant reader").
 */
public record UserSummary(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        String status
) {
}
