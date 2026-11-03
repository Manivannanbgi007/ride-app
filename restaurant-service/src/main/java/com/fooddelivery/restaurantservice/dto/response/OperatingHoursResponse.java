package com.fooddelivery.restaurantservice.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OperatingHoursResponse(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt
) {
}
