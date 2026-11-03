package com.fooddelivery.reviewservice.dto.request;

import java.time.LocalDate;

import com.fooddelivery.reviewservice.entity.ReviewTag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

/** Admin review list filters; all optional. "from" and "to" are inclusive days. */
public record ReviewSearchCriteria(

        @Parameter(description = "Reviews of one restaurant")
        Long restaurantId,

        @Parameter(description = "Only reviews that rate this dish (a dish rating for this menu item)")
        Long menuItemId,

        @Parameter(description = "Overall rating, 1 to 5")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        Integer rating,

        @Parameter(description = "Reviews with this tag")
        ReviewTag tag,

        @Parameter(description = "First day (yyyy-MM-dd, business time zone)", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Parameter(description = "Last day, inclusive (yyyy-MM-dd, business time zone)", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to
) {
}
