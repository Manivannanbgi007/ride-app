package com.fooddelivery.adminservice.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.fooddelivery.adminservice.exception.InvalidRequestException;

/**
 * Turns calendar days ("2026-10-01 to 2026-10-07", inclusive) into an exact time range
 * [from, before) in the business time zone. Storing Instants in UTC and converting only at the edges
 * means "today" is the same day for everyone, wherever the server runs.
 */
public record BusinessDateRange(Instant from, Instant before) {

    public static BusinessDateRange of(LocalDate fromDate, LocalDate toDate, ZoneId zone) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidRequestException("fromDate must not be after toDate");
        }
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant before = toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return new BusinessDateRange(from, before);
    }
}
