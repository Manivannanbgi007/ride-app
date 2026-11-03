package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.orderservice.dto.response.CustomerOrderSummaryResponse;
import com.fooddelivery.orderservice.repository.StatusTotals;

/** Adds up a customer's per-status totals (one grouped query) into their summary: the admin's and their own. */
final class CustomerSummaries {

    private CustomerSummaries() {
    }

    static CustomerOrderSummaryResponse of(Long customerId, List<StatusTotals> perStatus, Instant lastOrderAt,
                                           String currency) {
        long total = 0;
        long active = 0;
        long delivered = 0;
        long cancelled = 0;
        long rejected = 0;
        BigDecimal spent = BigDecimal.ZERO;

        for (StatusTotals totals : perStatus) {
            total += totals.getOrderCount();
            switch (totals.getStatus()) {
                case DELIVERED -> {
                    delivered += totals.getOrderCount();
                    spent = spent.add(totals.getTotalAmount());
                }
                case CANCELLED -> cancelled += totals.getOrderCount();
                case REJECTED -> rejected += totals.getOrderCount();
                default -> active += totals.getOrderCount();
            }
        }
        return new CustomerOrderSummaryResponse(customerId, total, active, delivered, cancelled, rejected,
                spent.setScale(2), lastOrderAt, currency);
    }
}
