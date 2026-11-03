package com.fooddelivery.restaurantservice.dto.response;

import java.time.Instant;
import java.util.List;

import com.fooddelivery.restaurantservice.entity.RestaurantStatus;

/** One row in the owner's or the admin's restaurant list. */
public record RestaurantSummaryResponse(
        Long id,
        String name,
        Long ownerUserId,
        List<String> cuisines,
        String city,
        RestaurantStatus status,
        String statusReason,
        Instant createdAt,
        Instant statusChangedAt
) {
}
