package com.fooddelivery.reviewservice.repository;

/**
 * The aggregate of one dish: {@code dishName} and {@code restaurantName} are snapshots saved in the reviews;
 * {@code averageRating} is not rounded yet.
 */
public record FeaturedDishRow(Long menuItemId, String dishName, Long restaurantId, String restaurantName,
                              Double averageRating, Long ratingCount) {
}
