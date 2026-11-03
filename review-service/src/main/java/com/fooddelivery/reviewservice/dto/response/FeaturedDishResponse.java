package com.fooddelivery.reviewservice.dto.response;

import java.math.BigDecimal;

/**
 * A dish with its rating in the period. {@code imageUrl} and {@code categoryName} come from catalog-service and are
 * null when it cannot say; {@code dishName} is then the name saved in the reviews.
 */
public record FeaturedDishResponse(
        Long menuItemId,
        String dishName,
        String imageUrl,
        String categoryName,
        Long restaurantId,
        String restaurantName,
        BigDecimal averageRating,
        long ratingCount,
        TopReviewResponse topReview
) {
}
