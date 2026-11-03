package com.fooddelivery.restaurantservice.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Size;

/** Optional filters for the public restaurant list. */
public record RestaurantSearchCriteria(

        @Parameter(description = "Restaurant name contains (case-insensitive)")
        @Size(max = 100)
        String search,

        @Parameter(description = "City (exact match, case-insensitive)", example = "Bengaluru")
        @Size(max = 100)
        String city,

        @Parameter(description = "Cuisine", example = "Biryani")
        @Size(max = 40)
        String cuisine
) {
}
