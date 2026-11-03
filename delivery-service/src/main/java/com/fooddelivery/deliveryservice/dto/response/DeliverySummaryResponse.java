package com.fooddelivery.deliveryservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.deliveryservice.entity.DeliveryStatus;

/**
 * One line of a delivery list.
 *
 * @param minutesSinceReady how long the food has been waiting or travelling (null once the delivery is finished)
 */
public record DeliverySummaryResponse(
        Long id,
        Long orderId,
        DeliveryStatus status,
        String restaurantName,
        String pickupCity,
        String dropoffCity,
        Long partnerUserId,
        String partnerName,
        BigDecimal cashToCollect,
        Instant readyAt,
        Instant assignedAt,
        Instant deliveredAt,
        Long minutesSinceReady
) {
}
