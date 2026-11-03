package com.fooddelivery.orderservice.dto.response;

import java.util.List;

import com.fooddelivery.orderservice.entity.OrderStatus;

/**
 * What review-service needs to accept a review of an order: whose order it is, whether it was delivered,
 * and which dishes it contained (a dish can only be rated if it was ordered).
 */
public record ReviewInfoResponse(
        Long id,
        Long customerId,
        String customerName,
        Long restaurantId,
        String restaurantName,
        OrderStatus status,
        List<Line> lines
) {

    /** One ordered dish. */
    public record Line(Long menuItemId, String name) {
    }
}
