package com.fooddelivery.deliverypartnerservice.dto.response;

import com.fooddelivery.deliverypartnerservice.entity.AccountStatus;
import com.fooddelivery.deliverypartnerservice.entity.VehicleType;

/**
 * Internal API (delivery-service): what the DeliveryPartnerChanged event carries, for a partner whose event has not
 * reached delivery-service yet.
 *
 * @param version version of the partner: delivery-service keeps the newest of this answer and the events
 */
public record InternalPartnerResponse(
        Long userId,
        String fullName,
        String phone,
        VehicleType vehicleType,
        String vehicleNumber,
        String city,
        AccountStatus accountStatus,
        String suspensionReason,
        long version
) {
}
