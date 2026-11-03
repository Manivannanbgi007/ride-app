package com.fooddelivery.deliveryservice.dto.response;

import java.time.Instant;

import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.entity.VehicleType;

/**
 * A delivery partner, for the partner themselves and for admins.
 *
 * @param readyToWork vehicle and city are filled in, so the partner can go online
 */
public record PartnerResponse(
        Long userId,
        String fullName,
        String phone,
        VehicleType vehicleType,
        String vehicleNumber,
        String city,
        PartnerStatus status,
        String statusReason,
        Long currentDeliveryId,
        Instant availableSince,
        int completedDeliveries,
        boolean readyToWork,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
