package com.fooddelivery.userservice.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.fooddelivery.userservice.exception.InvalidRequestException;

/**
 * A period of calendar days for statistics ("2026-10-01 to 2026-10-07", inclusive), as the exact time range
 * [from, before) in the business time zone. Both dates default to today; a period is at most 366 days.
 */
public record StatsPeriod(LocalDate fromDate, LocalDate toDate, Instant from, Instant before) {

    public static final int MAX_DAYS = 366;

    public static StatsPeriod of(LocalDate fromDate, LocalDate toDate, ZoneId zone, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = fromDate != null ? fromDate : today;
        LocalDate last = toDate != null ? toDate : today;
        if (first.isAfter(last)) {
            throw new InvalidRequestException("fromDate must not be after toDate");
        }
        if (ChronoUnit.DAYS.between(first, last) >= MAX_DAYS) {
            throw new InvalidRequestException("The period can be at most " + MAX_DAYS + " days");
        }
        return new StatsPeriod(first, last, first.atStartOfDay(zone).toInstant(),
                last.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
