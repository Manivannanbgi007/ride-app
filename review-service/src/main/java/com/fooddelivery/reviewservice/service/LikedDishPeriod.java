package com.fooddelivery.reviewservice.service;

import java.time.Duration;
import java.time.Instant;

/** How far back the ratings count, by the review's created time: ALL, the last 30 days or the last 7 days. */
public enum LikedDishPeriod {

    ALL(null),
    MONTHLY(Duration.ofDays(30)),
    WEEKLY(Duration.ofDays(7));

    private final Duration length;

    LikedDishPeriod(Duration length) {
        this.length = length;
    }

    /** Null means no lower limit. */
    public Instant start(Instant now) {
        return length == null ? null : now.minus(length);
    }
}
