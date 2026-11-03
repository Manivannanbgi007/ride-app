package com.fooddelivery.deliverypartnerservice.dto.response;

import java.time.Instant;

import com.fooddelivery.deliverypartnerservice.entity.PartnerStatus;
import com.fooddelivery.deliverypartnerservice.entity.VehicleType;

/**
 * A delivery partner, for the partner themselves and for admins. Same shape as when delivery-service served it, so
 * the web application did not change when partners moved here.
 *
 * @param status            SUSPENDED while the account is suspended, otherwise what delivery-service reports
 *                          (OFFLINE, AVAILABLE, BUSY)
 * @param currentDeliveryId the delivery the partner is on (as last reported by delivery-service)
 * @param readyToWork       vehicle and city are filled in, so the partner can go online
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
