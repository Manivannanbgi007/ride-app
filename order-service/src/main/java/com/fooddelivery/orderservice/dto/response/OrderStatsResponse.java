package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;

/**
 * Order numbers for a period (orders placed from fromDate to toDate, calendar days in the business time zone),
 * plus figures that do not depend on the period. Revenue is the total of delivered orders.
 *
 * @param completed         delivered
 * @param cancelled         cancelled or rejected
 * @param inProgress        not finished yet (waiting for payment, at the restaurant or on the way)
 * @param discounts         coupon discounts on the delivered orders
 * @param averageOrderValue revenue / completed (0 when nothing was delivered)
 * @param perDay            every day of the period, also days without orders (for charts)
 */
public record OrderStatsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        long orders,
        long completed,
        long cancelled,
        long inProgress,
        BigDecimal revenue,
        BigDecimal discounts,
        BigDecimal averageOrderValue,
        Map<OrderStatus, Long> byStatus,
        Map<PaymentMethod, MethodTotals> byPaymentMethod,
        List<Day> perDay,
        Overall overall
) {

    public record MethodTotals(long orders, long completed, BigDecimal revenue) {
    }

    public record Day(LocalDate date, long orders, long completed, long cancelled, BigDecimal revenue) {
    }

    /**
     * Independent of the period.
     *
     * @param inProgress orders not finished right now, whenever they were placed
     */
    public record Overall(long orders, long completed, long inProgress, BigDecimal revenue) {
    }
}
