package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A customer's order history in numbers: on the admin user screen, and on the customer's own dashboard. Every order
 * counts in totalOrders; active = any status that is not finished (waiting for payment included); totalSpent = what
 * the delivered orders cost (cancelled and rejected ones were not paid for, or were refunded).
 */
public record CustomerOrderSummaryResponse(
        Long customerId,
        long totalOrders,
        long activeOrders,
        long deliveredOrders,
        long cancelledOrders,
        long rejectedOrders,
        BigDecimal totalSpent,
        Instant lastOrderAt,
        String currency
) {
}
