package com.fooddelivery.reviewservice.dto.response;

import java.time.Instant;
import java.util.List;

import com.fooddelivery.reviewservice.entity.ReviewTag;

/**
 * A review as any visitor sees it. Private data stays out: no customer id, no order id and only a short form of the
 * customer's name (see ReviewerNames).
 */
public record PublicReviewResponse(
        Long id,
        String reviewerName,
        int rating,
        String comment,
        List<ReviewTag> tags,
        List<DishRatingResponse> dishes,
        Instant createdAt,
        Instant updatedAt
) {
}
