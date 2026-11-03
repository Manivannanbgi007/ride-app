package com.fooddelivery.deliveryservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.deliveryservice.entity.ActorType;
import com.fooddelivery.deliveryservice.entity.DeliveryEventType;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.VehicleType;

/**
 * A delivery with everything needed to carry it out (for its partner) or to follow it up (for admins).
 *
 * @param cashToCollect cash on delivery: what the partner collects at the door (0 for paid orders)
 */
public record DeliveryResponse(
        Long id,
        Long orderId,
        DeliveryStatus status,
        Long restaurantId,
        String restaurantName,
        AddressResponse pickupAddress,
        Long customerId,
        String customerName,
        String customerPhone,
        AddressResponse dropoffAddress,
        String paymentMethod,
        BigDecimal cashToCollect,
        BigDecimal cashCollected,
        String currency,
        Long partnerUserId,
        String partnerName,
        VehicleType vehicleType,
        String cancellationReason,
        Instant readyAt,
        Instant assignedAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<TimelineEntry> timeline
) {

    public record TimelineEntry(DeliveryEventType event, DeliveryStatus status, Long partnerUserId,
                                ActorType actorType, Long actorUserId, String note, Instant at) {
    }
}
