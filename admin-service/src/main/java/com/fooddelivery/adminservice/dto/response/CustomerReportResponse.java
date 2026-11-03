package com.fooddelivery.adminservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.CustomerRow;

/**
 * Customer report: accounts (user-service) and ordering (order-service).
 *
 * @param totalCustomers      customer accounts now
 * @param activeAccounts      accounts that can sign in and order
 * @param deactivatedAccounts accounts an admin deactivated
 * @param newCustomers        signed up in the period
 * @param orderingCustomers   placed at least one order in the period ("active customers")
 * @param notOrdering         customer accounts without an order in the period (inactive customers)
 * @param orderFrequency      customers who placed 1, 2-3, 4-5 or 6+ orders in the period
 */
public record CustomerReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        Long totalCustomers,
        Long activeAccounts,
        Long deactivatedAccounts,
        Long newCustomers,
        Long orderingCustomers,
        Long notOrdering,
        BigDecimal ordersPerCustomer,
        Map<String, Long> orderFrequency,
        List<CustomerRow> topCustomers,
        List<String> unavailable
) {
}
