package com.fooddelivery.restaurantservice.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** One opening slot, for example MONDAY 11:00-15:00. Times are local to the restaurant's time zone. */
@Embeddable
public record OperatingHours(

        @Enumerated(EnumType.STRING)
        @Column(name = "day_of_week", nullable = false, length = 10)
        DayOfWeek dayOfWeek,

        @Column(name = "opens_at", nullable = false)
        LocalTime opensAt,

        @Column(name = "closes_at", nullable = false)
        LocalTime closesAt
) {
}
