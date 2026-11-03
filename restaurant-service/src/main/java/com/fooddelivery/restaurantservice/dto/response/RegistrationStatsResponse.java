package com.fooddelivery.restaurantservice.dto.response;

import java.time.LocalDate;
import java.util.List;

/** Restaurants submitted per business day (for the admin calendar); only days with at least one are listed. */
public record RegistrationStatsResponse(List<Day> days) {

    public record Day(LocalDate date, long registered) {
    }
}
