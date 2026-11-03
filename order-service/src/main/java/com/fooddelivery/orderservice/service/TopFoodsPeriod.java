package com.fooddelivery.orderservice.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * How far back "most ordered dishes" look (business time zone): MONTHLY = the last 30 days, WEEKLY = the last
 * 7 days, DAILY = today since business midnight. Rolling windows, not calendar months, so a customer's list
 * does not empty out on the 1st of the month.
 */
public enum TopFoodsPeriod {

    MONTHLY(Duration.ofDays(30)),
    WEEKLY(Duration.ofDays(7)),
    DAILY(null);

    private final Duration length;

    TopFoodsPeriod(Duration length) {
        this.length = length;
    }

    public Instant start(Instant now, ZoneId zone) {
        return length == null ? now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant() : now.minus(length);
    }
}
