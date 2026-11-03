package com.fooddelivery.reviewservice.dto.request;

import java.util.List;

import com.fooddelivery.reviewservice.entity.ReviewTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The customer's edit of their own review: the same fields and rules as {@link CreateReviewRequest} (without the
 * order, which cannot change). The tags and the dish ratings replace the saved ones: a dish that is left out is no
 * longer rated.
 *
 * @param version the version of the review the customer saw (from the review response); optional. When it is sent and
 *                the review was changed since, the edit is refused with 409
 */
public record UpdateReviewRequest(

        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        Integer rating,

        @Size(max = 1000, message = "comment must be at most 1000 characters")
        String comment,

        List<ReviewTag> tags,

        @Size(max = 50, message = "at most 50 dish ratings")
        List<@Valid DishRatingRequest> dishRatings,

        Long version
) {
}
