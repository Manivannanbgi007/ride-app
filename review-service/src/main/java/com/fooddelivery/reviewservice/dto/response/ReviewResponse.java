package com.fooddelivery.reviewservice.dto.response;

import java.time.Instant;
import java.util.List;

import com.fooddelivery.reviewservice.entity.ReviewTag;

/**
 * @param updatedAt when the review was last edited (the creation time until then)
 * @param version   send it back when editing, so an edit of an outdated review is refused
 */
public record ReviewResponse(
        Long id,
        Long orderId,
        Long customerId,
        String customerName,
        Long restaurantId,
        String restaurantName,
        int rating,
        String comment,
        List<ReviewTag> tags,
        List<DishRatingResponse> dishRatings,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
