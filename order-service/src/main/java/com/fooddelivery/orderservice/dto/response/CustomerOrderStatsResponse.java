package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Customers' ordering in a period.
 *
 * @param customersWithOrders customers who placed at least one order
 * @param ordersPerCustomer   orders / customersWithOrders
 * @param orderFrequency      customers who placed 1, 2-3, 4-5 or 6+ orders
 * @param topBySpend          customers who spent most on delivered orders
 */
public record CustomerOrderStatsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        long customersWithOrders,
        long orders,
        BigDecimal ordersPerCustomer,
        Map<String, Long> orderFrequency,
        List<Customer> topBySpend
) {

    public record Customer(Long customerId, String customerName, long orders, long completed, BigDecimal spent) {
    }
}
