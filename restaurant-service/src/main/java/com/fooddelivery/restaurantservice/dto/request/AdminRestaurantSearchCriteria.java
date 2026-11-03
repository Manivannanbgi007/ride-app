package com.fooddelivery.restaurantservice.dto.request;

import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Size;

/** Optional filters for the admin restaurant list. */
public record AdminRestaurantSearchCriteria(

        @Parameter(description = "Matches name, legal business name or city (contains, case-insensitive)")
        @Size(max = 100)
        String search,

        @Parameter(description = "Filter by status, for example PENDING to see approvals waiting")
        RestaurantStatus status,

        @Parameter(description = "City (exact match, case-insensitive)")
        @Size(max = 100)
        String city
) {
}
