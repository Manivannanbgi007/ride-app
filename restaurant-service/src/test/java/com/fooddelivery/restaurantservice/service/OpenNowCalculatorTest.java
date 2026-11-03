package com.fooddelivery.restaurantservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.restaurantservice.entity.OperatingHours;
import org.junit.jupiter.api.Test;

class OpenNowCalculatorTest {

    // Wednesday 2026-10-07 07:00 UTC = 12:30 in India (UTC+5:30)
    private static final Instant WEDNESDAY_NOON_IN_INDIA = Instant.parse("2026-10-07T07:00:00Z");

    private final OpenNowCalculator calculator =
            new OpenNowCalculator(Clock.fixed(WEDNESDAY_NOON_IN_INDIA, ZoneOffset.UTC));

    @Test
    void openDuringASlotInTheRestaurantsTimeZone() {
        List<OperatingHours> hours = List.of(slot(DayOfWeek.WEDNESDAY, "11:00", "15:00"));

        assertThat(calculator.isOpenNow(hours, "Asia/Kolkata")).isTrue();
    }

    @Test
    void sameScheduleIsClosedInATimeZoneWhereItIsStillMorning() {
        // 07:00 in London: before the 11:00 opening
        List<OperatingHours> hours = List.of(slot(DayOfWeek.WEDNESDAY, "11:00", "15:00"));

        assertThat(calculator.isOpenNow(hours, "Europe/London")).isFalse();
    }

    @Test
    void closedOnADayWithoutSlots() {
        List<OperatingHours> hours = List.of(slot(DayOfWeek.MONDAY, "00:00", "23:59"));

        assertThat(calculator.isOpenNow(hours, "Asia/Kolkata")).isFalse();
    }

    @Test
    void closingTimeIsExclusive() {
        List<OperatingHours> hours = List.of(slot(DayOfWeek.WEDNESDAY, "09:00", "12:30"));

        assertThat(calculator.isOpenNow(hours, "Asia/Kolkata")).isFalse();
    }

    private static OperatingHours slot(DayOfWeek day, String opensAt, String closesAt) {
        return new OperatingHours(day, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }
}
