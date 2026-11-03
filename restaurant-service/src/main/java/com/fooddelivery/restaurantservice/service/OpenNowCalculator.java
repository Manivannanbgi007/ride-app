package com.fooddelivery.restaurantservice.service;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.fooddelivery.restaurantservice.entity.OperatingHours;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Decides whether a restaurant is open right now, in the restaurant's own time zone. */
@Component
@RequiredArgsConstructor
public class OpenNowCalculator {

    private final Clock clock;

    public boolean isOpenNow(List<OperatingHours> operatingHours, String timeZone) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneId.of(timeZone)));
        LocalTime time = now.toLocalTime();

        return operatingHours.stream()
                .filter(slot -> now.getDayOfWeek().equals(slot.dayOfWeek()))
                .anyMatch(slot -> !time.isBefore(slot.opensAt()) && time.isBefore(slot.closesAt()));
    }
}
