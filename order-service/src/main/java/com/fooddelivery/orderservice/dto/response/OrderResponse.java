package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.entity.PaymentStatus;

/** Full order with its timeline: what customers track and what restaurants and admins work on. */
public record OrderResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerPhone,
        Long restaurantId,
        String restaurantName,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        List<Line> items,
        PriceBreakdown price,
        String couponCode,
        Address deliveryAddress,
        String notes,
        String cancellationReason,
        ActorType cancelledBy,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<TimelineEntry> timeline
) {

    public record Line(Long menuItemId, String name, String foodType, BigDecimal unitPrice, int quantity,
                       BigDecimal lineTotal) {
    }

    public record Address(String line1, String line2, String city, String state, String postalCode, String country,
                          BigDecimal latitude, BigDecimal longitude) {
    }

    public record TimelineEntry(OrderStatus status, ActorType actorType, Long actorUserId, String note, Instant at) {
    }
}
