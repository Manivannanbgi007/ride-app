package com.fooddelivery.restaurantservice.dto.request;

import java.time.DayOfWeek;
import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OperatingHoursRequest(

        @NotNull
        DayOfWeek dayOfWeek,

        @Schema(example = "11:00")
        @NotNull
        LocalTime opensAt,

        @Schema(example = "23:00")
        @NotNull
        LocalTime closesAt
) {
}
