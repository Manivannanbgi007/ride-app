package com.fooddelivery.reviewservice.client;

import java.util.List;

/**
 * What order-service says about an order that someone wants to review (its internal "review-info" answer).
 *
 * @param status the order status name, for example DELIVERED
 * @param lines  the dishes of the order: only these can be rated
 */
public record OrderReviewInfo(Long id, Long customerId, String customerName, Long restaurantId, String restaurantName,
                              String status, List<Line> lines) {

    public static final String DELIVERED = "DELIVERED";

    public OrderReviewInfo {
        lines = lines == null ? List.of() : lines;
    }

    public record Line(Long menuItemId, String name) {
    }

    public boolean isDelivered() {
        return DELIVERED.equals(status);
    }
}
