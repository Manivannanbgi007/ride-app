package com.fooddelivery.orderservice.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Orders per business day for the admin calendar; only days with at least one order are listed.
 * All three counts are by the day the order was placed.
 *
 * @param placed    all orders created that day
 * @param delivered DELIVERED orders
 * @param cancelled CANCELLED and REJECTED orders
 */
public record CalendarStatsResponse(List<Day> days) {

    public record Day(LocalDate date, long placed, long delivered, long cancelled) {
    }
}
