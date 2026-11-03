package com.fooddelivery.restaurantservice.dto.response;

import java.time.LocalDate;
import java.util.Map;

import com.fooddelivery.restaurantservice.entity.RestaurantStatus;

/**
 * Restaurant numbers for the admin dashboard and the restaurant report.
 *
 * @param restaurantsNow       all restaurants right now, per status
 * @param pendingApproval      submitted and waiting for an admin's decision (PENDING)
 * @param newRestaurants       restaurants submitted in the period
 * @param newRestaurantsByStatus where the restaurants submitted in the period are now
 */
public record RestaurantStatsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Map<RestaurantStatus, Long> restaurantsNow,
        long total,
        long active,
        long inactive,
        long suspended,
        long pendingApproval,
        long newRestaurants,
        Map<RestaurantStatus, Long> newRestaurantsByStatus
) {
}
