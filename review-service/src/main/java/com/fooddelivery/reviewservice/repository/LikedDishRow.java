package com.fooddelivery.reviewservice.repository;

import java.time.Instant;

/** The aggregate of one dish for one customer: {@code averageRating} is not rounded yet. */
public record LikedDishRow(Long menuItemId, String dishName, Double averageRating, Long ratingCount,
                           Instant lastRatedAt) {
}
