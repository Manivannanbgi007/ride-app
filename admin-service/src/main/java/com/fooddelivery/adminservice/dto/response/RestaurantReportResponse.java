package com.fooddelivery.adminservice.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantRow;

/**
 * Restaurant report: restaurants now (restaurant-service) and how they did in the period (order-service).
 *
 * @param newRestaurants        submitted in the period
 * @param restaurantsWithOrders received at least one order in the period
 * @param lowPerforming         restaurants with orders, least revenue first (restaurants without any order are
 *                              not listed: compare restaurantsWithOrders with active)
 */
public record RestaurantReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        Long total,
        Long active,
        Long inactive,
        Long suspended,
        Long pendingApproval,
        Long newRestaurants,
        Map<String, Long> byStatus,
        Long restaurantsWithOrders,
        List<RestaurantRow> topRestaurants,
        List<RestaurantRow> lowPerforming,
        List<RestaurantRow> mostCancellations,
        List<String> unavailable
) {
}
