package com.fooddelivery.catalogservice.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.fooddelivery.catalogservice.entity.FoodType;

/** A restaurant's menu as customers see it: listed items grouped by category, in category order. */
public record MenuResponse(
        Long restaurantId,
        String restaurantName,
        List<Section> sections
) {

    public record Section(
            Long categoryId,
            String categoryName,
            List<Item> items
    ) {
    }

    /** No status, version or audit fields: customers only need what to show and order. */
    public record Item(
            Long id,
            String name,
            String description,
            BigDecimal price,
            FoodType foodType,
            String imageUrl,
            boolean available
    ) {
    }
}
