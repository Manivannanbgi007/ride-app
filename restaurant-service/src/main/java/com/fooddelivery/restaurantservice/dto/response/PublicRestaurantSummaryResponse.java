package com.fooddelivery.restaurantservice.dto.response;

import java.util.List;

/** One restaurant in the customer-facing list. No business or owner data. */
public record PublicRestaurantSummaryResponse(
        Long id,
        String name,
        List<String> cuisines,
        String city,
        boolean openNow
) {
}
