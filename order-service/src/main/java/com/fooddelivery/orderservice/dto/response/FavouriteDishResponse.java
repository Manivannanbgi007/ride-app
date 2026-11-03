package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A dish the customer ordered before, as their dashboard's "Order again" shows it. The name, restaurant, order count
 * and last order come from their own orders; the price, stock, picture and category are catalog-service's current
 * ones (what adding it to the cart would cost now).
 *
 * @param price     today's price; null when the dish is no longer on any menu
 * @param foodType  VEG, NON_VEG or EGG; null when the dish is no longer on any menu
 * @param available on the menu and in stock now, so it can be added to the cart
 * @param timesOrdered in how many of the customer's orders it was (cancelled, rejected and unpaid ones left out)
 */
public record FavouriteDishResponse(
        Long menuItemId,
        String name,
        Long restaurantId,
        String restaurantName,
        BigDecimal price,
        String foodType,
        String imageUrl,
        String categoryName,
        boolean available,
        long timesOrdered,
        long quantity,
        Instant lastOrderedAt
) {
}
