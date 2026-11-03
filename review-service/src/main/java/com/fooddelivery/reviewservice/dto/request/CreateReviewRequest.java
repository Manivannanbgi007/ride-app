package com.fooddelivery.reviewservice.dto.request;

import java.util.List;

import com.fooddelivery.reviewservice.entity.ReviewTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A customer review of a delivered order. The tag count (at most 4, each once) and the dishes (each must be a
 * line of the order, each once) are checked in ReviewRules, because they need more than one field or the order.
 */
public record CreateReviewRequest(

        @NotNull(message = "orderId is required")
        Long orderId,

        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        Integer rating,

        @Size(max = 1000, message = "comment must be at most 1000 characters")
        String comment,

        List<ReviewTag> tags,

        @Size(max = 50, message = "at most 50 dish ratings")
        List<@Valid DishRatingRequest> dishRatings
) {
}
