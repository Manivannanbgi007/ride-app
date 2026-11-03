package com.fooddelivery.restaurantservice.dto.response;

import java.time.Instant;

import com.fooddelivery.restaurantservice.entity.DocumentType;

public record DocumentResponse(
        DocumentType type,
        String url,
        Instant submittedAt
) {
}
