package com.fooddelivery.searchservice.dto.response;

import java.math.BigDecimal;

/**
 * A dish with what a result list shows of its restaurant and category.
 *
 * @param ratingAverage the customers' average rating, 1.0 to 5.0 (null while the dish has no rating)
 * @param ratingCount   how many ratings that average is made of (0 while the dish has none)
 */
public record FoodHit(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String foodType,
        String imageUrl,
        Long categoryId,
        String categoryName,
        Long restaurantId,
        String restaurantName,
        String restaurantCity,
        Double ratingAverage,
        int ratingCount
) {
}
