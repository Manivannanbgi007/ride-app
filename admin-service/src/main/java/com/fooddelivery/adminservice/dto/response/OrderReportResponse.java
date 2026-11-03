package com.fooddelivery.adminservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.CityRow;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantRow;

/**
 * Order report: orders placed in the period. Null figures and empty lists: the service did not answer (see
 * unavailable).
 *
 * @param pending      not finished yet (waiting for payment, at the restaurant or on the way)
 * @param perDay       orders by date (every day of the period)
 * @param byRestaurant restaurants with the most revenue, with their order counts
 * @param byCity       orders by delivery city
 */
public record OrderReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        Long totalOrders,
        Long completed,
        Long cancelled,
        Long pending,
        BigDecimal averageOrderValue,
        Map<String, Long> byStatus,
        List<DashboardResponse.DayPoint> perDay,
        List<RestaurantRow> byRestaurant,
        List<CityRow> byCity,
        List<String> unavailable
) {
}
