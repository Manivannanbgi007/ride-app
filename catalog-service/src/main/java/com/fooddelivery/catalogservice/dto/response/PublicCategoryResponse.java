package com.fooddelivery.catalogservice.dto.response;

/** Category shown to customers (for example as filter chips). */
public record PublicCategoryResponse(
        Long id,
        String name,
        String description,
        String imageUrl
) {
}
