package com.fooddelivery.reviewservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The dishes a customer rated highest. {@code ratingCount} is all the customer's dish ratings in the period,
 * also those of dishes that did not make the list.
 */
public record CustomerLikedDishesResponse(
        long customerId,
        String period,
        long ratingCount,
        List<Dish> dishes
) {

    public record Dish(long menuItemId, String dishName, BigDecimal averageRating, long ratingCount,
                       Instant lastRatedAt) {
    }
}
