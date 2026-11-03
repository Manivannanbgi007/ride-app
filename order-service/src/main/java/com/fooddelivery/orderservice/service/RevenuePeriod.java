package com.fooddelivery.orderservice.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Bucket size of a revenue chart and how many buckets it shows (the current one included, oldest first). */
public enum RevenuePeriod {

    MONTHLY(12, "MMM yyyy"),
    WEEKLY(12, "d MMM"),
    DAILY(30, "d MMM");

    private final int buckets;
    private final DateTimeFormatter labelFormat;

    RevenuePeriod(int buckets, String labelPattern) {
        this.buckets = buckets;
        this.labelFormat = DateTimeFormatter.ofPattern(labelPattern, Locale.ENGLISH);
    }

    public int buckets() {
        return buckets;
    }

    /** The first day of the bucket that contains the day (months start on the 1st, weeks on Monday). */
    public LocalDate bucketStart(LocalDate day) {
        return switch (this) {
            case MONTHLY -> day.withDayOfMonth(1);
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case DAILY -> day;
        };
    }

    public LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case MONTHLY -> bucketStart.plusMonths(1);
            case WEEKLY -> bucketStart.plusWeeks(1);
            case DAILY -> bucketStart.plusDays(1);
        };
    }

    /** The start of the oldest bucket shown when today is the given day. */
    public LocalDate firstBucketStart(LocalDate today) {
        LocalDate current = bucketStart(today);
        return switch (this) {
            case MONTHLY -> current.minusMonths(buckets - 1L);
            case WEEKLY -> current.minusWeeks(buckets - 1L);
            case DAILY -> current.minusDays(buckets - 1L);
        };
    }

    /** "Oct 2026" (months), "6 Oct" (weeks: the Monday, and days). */
    public String label(LocalDate bucketStart) {
        return labelFormat.format(bucketStart);
    }
}
