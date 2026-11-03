package com.fooddelivery.deliveryservice.dto.response;

import java.time.Instant;

import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.entity.VehicleType;

/**
 * Internal API (delivery-partner-service's one-off import): a partner as this service's dispatch list has them.
 *
 * @param suspended        suspended by an admin (the partner may still be finishing a delivery)
 * @param version          version of this record: delivery-partner-service applies later reports only if newer
 */
public record RosterPartnerResponse(
        Long userId,
        String fullName,
        String phone,
        VehicleType vehicleType,
        String vehicleNumber,
        String city,
        PartnerStatus status,
        String statusReason,
        boolean suspended,
        String suspensionReason,
        Long currentDeliveryId,
        Instant availableSince,
        int completedDeliveries,
        Long version
) {
}
