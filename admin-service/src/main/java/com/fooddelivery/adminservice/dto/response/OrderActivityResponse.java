package com.fooddelivery.adminservice.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * The dashboard's order charts ("Chart Order" and "Order Rates") for one chosen day, week, month or any other
 * period. Same figures as the dashboard's charts, for the period asked for.
 *
 * @param fromDate       first day covered (business time zone)
 * @param toDate         last day covered: the day asked for, or today when that is still to come
 * @param ordersPerDay   orders and revenue per day (every day, also without orders)
 * @param paymentOutcome online payments of the period; null when the payment service did not answer
 * @param unavailable    services that did not answer: their figures are null and their lists empty
 */
public record OrderActivityResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        List<DashboardResponse.DayPoint> ordersPerDay,
        DashboardResponse.PaymentOutcome paymentOutcome,
        List<String> unavailable
) {
}
