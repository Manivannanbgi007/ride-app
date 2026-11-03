package com.fooddelivery.adminservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantRow;
import com.fooddelivery.adminservice.dto.request.RevenueGroupBy;

/**
 * Revenue report. Revenue is the total customers paid for delivered orders, by the day the order was placed.
 *
 * @param periods         revenue per day, week or month (groupBy)
 * @param byRestaurant    restaurants with the most revenue
 * @param byPaymentMethod delivered orders and revenue per payment method (cash on delivery included)
 * @param onlinePayments  money taken and given back online in the period (payment-service)
 */
public record RevenueReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        RevenueGroupBy groupBy,
        BigDecimal revenue,
        BigDecimal discounts,
        BigDecimal averageOrderValue,
        List<Period> periods,
        List<RestaurantRow> byRestaurant,
        Map<String, MethodRevenue> byPaymentMethod,
        OnlinePayments onlinePayments,
        List<String> unavailable
) {

    /** @param label 2026-10-07, 2026-W41 or 2026-10 */
    public record Period(String label, LocalDate from, LocalDate to, long orders, long completed, BigDecimal revenue) {
    }

    public record MethodRevenue(long orders, long completed, BigDecimal revenue) {
    }

    public record OnlinePayments(BigDecimal collected, BigDecimal refunded) {
    }
}
