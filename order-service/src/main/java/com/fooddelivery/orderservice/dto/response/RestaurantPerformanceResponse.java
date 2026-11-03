package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * How restaurants did in a period, from the orders they received (unpaid online orders that expired are not
 * counted: the restaurant never saw them).
 *
 * @param restaurantsWithOrders restaurants that received at least one order in the period
 * @param lowestByRevenue       restaurants with orders, least revenue first (restaurants without any order are
 *                              not listed here: compare restaurantsWithOrders with the active restaurants)
 */
public record RestaurantPerformanceResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        long restaurantsWithOrders,
        List<Restaurant> topByRevenue,
        List<Restaurant> lowestByRevenue,
        List<Restaurant> mostCancellations
) {

    /** @param cancellationRate cancelled or rejected orders, in percent of the orders */
    public record Restaurant(Long restaurantId, String restaurantName, long orders, long completed, long cancelled,
                             BigDecimal revenue, BigDecimal cancellationRate) {
    }
}
