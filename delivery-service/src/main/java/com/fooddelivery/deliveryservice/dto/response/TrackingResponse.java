package com.fooddelivery.deliveryservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.VehicleType;

/**
 * What a customer sees about the delivery of their order.
 *
 * @param partnerName  who brings the food (once a partner accepted)
 * @param partnerPhone to call the partner; only while they are on the way
 * @param steps        the milestones so far (internal steps such as offers to partners are not shown)
 */
public record TrackingResponse(
        Long orderId,
        Long deliveryId,
        DeliveryStatus status,
        String restaurantName,
        AddressResponse dropoffAddress,
        String partnerName,
        String partnerPhone,
        VehicleType vehicleType,
        BigDecimal cashToCollect,
        String currency,
        Instant readyAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        Instant cancelledAt,
        List<Step> steps
) {

    public record Step(DeliveryStatus status, String note, Instant at) {
    }
}
