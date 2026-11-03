package com.fooddelivery.reviewservice.dto.response;

/** The rating of one dish; the name is the one on the order when the review was written. */
public record DishRatingResponse(Long menuItemId, String dishName, int rating) {
}
