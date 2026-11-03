package com.fooddelivery.reviewservice.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

/** The filters shared by the admin summary and featured dishes; all optional. "from" and "to" are inclusive days. */
public record ReviewFilter(

        @Parameter(description = "Reviews of one restaurant")
        Long restaurantId,

        @Parameter(description = "First day (yyyy-MM-dd, business time zone)", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Parameter(description = "Last day, inclusive (yyyy-MM-dd, business time zone)", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to
) {
}
