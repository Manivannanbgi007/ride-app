package com.fooddelivery.restaurantservice.dto.response;

import java.time.Instant;
import java.util.List;

import com.fooddelivery.restaurantservice.entity.RestaurantStatus;

/** Everything about a restaurant, for its owner and for platform admins. */
public record RestaurantDetailsResponse(
        Long id,
        Long ownerUserId,
        String name,
        String description,
        List<String> cuisines,
        String phone,
        String email,
        AddressResponse address,
        String timeZone,
        String legalBusinessName,
        String taxRegistrationNumber,
        String foodLicenseNumber,
        RestaurantStatus status,
        String statusReason,
        Instant statusChangedAt,
        Instant approvedAt,
        List<OperatingHoursResponse> operatingHours,
        List<DocumentResponse> documents,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
