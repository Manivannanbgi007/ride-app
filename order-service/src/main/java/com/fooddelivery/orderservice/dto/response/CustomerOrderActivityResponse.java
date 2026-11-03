package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What a customer ordered in a period (their dashboard's charts). Days are business days, both ends included.
 *
 * @param totalOrders    every order placed in the period, whatever its status now
 * @param ordersByStatus the period's orders by the status they have now (statuses without orders are left out)
 * @param spent          what the period's delivered orders cost (cancelled and rejected ones are not spending)
 * @param perDay         every day of the period: orders placed, delivered, cancelled or rejected, and spent
 */
public record CustomerOrderActivityResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long totalOrders,
        Map<String, Long> ordersByStatus,
        BigDecimal spent,
        String currency,
        List<OrderStatsResponse.Day> perDay
) {
}
