package com.fooddelivery.reviewservice.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * The filters of the admin summary; all optional. "from" and "to" are inclusive days. With a menu item the summary
 * is about the ratings given to that dish, not the overall review ratings (see ReviewAdminService.summary).
 */
public record ReviewSummaryFilter(

        @Parameter(description = "Reviews of one restaurant")
        Long restaurantId,

        @Parameter(description = "Only reviews that rate this dish; the summary then counts the dish's own ratings")
        Long menuItemId,

        @Parameter(description = "First day (yyyy-MM-dd, business time zone)", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Parameter(description = "Last day, inclusive (yyyy-MM-dd, business time zone)", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to
) {
}
