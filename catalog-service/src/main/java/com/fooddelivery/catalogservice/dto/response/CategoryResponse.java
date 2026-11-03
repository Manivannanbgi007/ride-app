package com.fooddelivery.catalogservice.dto.response;

import java.time.Instant;

import com.fooddelivery.catalogservice.entity.CategoryStatus;

/** Admin view of a category, including how many menu items use it. */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        int displayOrder,
        CategoryStatus status,
        long menuItemCount,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
