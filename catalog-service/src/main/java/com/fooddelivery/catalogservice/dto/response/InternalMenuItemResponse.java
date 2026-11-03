package com.fooddelivery.catalogservice.dto.response;

import java.math.BigDecimal;

import com.fooddelivery.catalogservice.entity.FoodType;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;

/**
 * Current menu item data for other microservices (order-service prices carts and orders with it;
 * review-service shows the photo and category of a rated dish).
 */
public record InternalMenuItemResponse(
        Long id,
        Long restaurantId,
        String name,
        BigDecimal price,
        FoodType foodType,
        MenuItemStatus status,
        boolean available,
        String imageUrl,
        String categoryName
) {
}
