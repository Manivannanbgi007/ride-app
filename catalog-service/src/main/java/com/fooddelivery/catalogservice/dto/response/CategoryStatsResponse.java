package com.fooddelivery.catalogservice.dto.response;

/** How many menu items a category has and how many of them customers can order right now. */
public record CategoryStatsResponse(
        Long categoryId,
        String categoryName,
        long totalItems,
        long orderableItems,
        int orderablePercent
) {
}
