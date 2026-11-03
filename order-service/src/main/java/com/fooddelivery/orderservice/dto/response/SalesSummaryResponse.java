package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.entity.OrderStatus;

/**
 * A restaurant's sales for a period.
 *
 * @param acceptedOrdersValue  value of orders the restaurant accepted (in progress or delivered)
 * @param deliveredOrdersValue value of delivered orders only
 * @param perDay               each day of the period: orders placed, delivered and cancelled or rejected, and the
 *                             delivered orders' value (the same definitions as the admin's per-day figures)
 * @param perHour              each hour of the day (0-23, business time zone), summed over every day of the period:
 *                             the same figures as perDay, always 24 entries (also hours without orders)
 */
public record SalesSummaryResponse(
        Long restaurantId,
        LocalDate fromDate,
        LocalDate toDate,
        long totalOrders,
        Map<OrderStatus, Long> ordersByStatus,
        BigDecimal acceptedOrdersValue,
        BigDecimal deliveredOrdersValue,
        String currency,
        List<OrderStatsResponse.Day> perDay,
        List<Hour> perHour
) {

    /** One hour of the day (13 = from 13:00 to 14:00): orders placed in it, delivered, cancelled or rejected, sales. */
    public record Hour(int hour, long orders, long completed, long cancelled, BigDecimal revenue) {
    }
}
