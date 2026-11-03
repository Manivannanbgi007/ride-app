package com.fooddelivery.reviewservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The rating of one dish of the order. */
public record DishRatingRequest(

        @NotNull(message = "menuItemId is required")
        Long menuItemId,

        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        Integer rating
) {
}
