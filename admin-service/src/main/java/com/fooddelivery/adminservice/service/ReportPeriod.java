package com.fooddelivery.adminservice.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.fooddelivery.adminservice.exception.InvalidRequestException;

/**
 * The calendar days a report covers (inclusive, business time zone). Without dates: the last 30 days up to today;
 * with only toDate: the 30 days up to it. At most 366 days, like the services' statistics.
 */
public record ReportPeriod(LocalDate fromDate, LocalDate toDate) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;

    public static ReportPeriod of(LocalDate fromDate, LocalDate toDate, LocalDate today) {
        LocalDate to = toDate != null ? toDate : today;
        LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new InvalidRequestException("fromDate must not be after toDate");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new InvalidRequestException("A report covers at most " + MAX_DAYS + " days");
        }
        return new ReportPeriod(from, to);
    }
}
