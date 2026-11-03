package com.fooddelivery.restaurantservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Reference to an onboarding document stored in document storage. */
@Embeddable
public record RestaurantDocument(

        @Enumerated(EnumType.STRING)
        @Column(name = "document_type", nullable = false, length = 30)
        DocumentType type,

        @Column(name = "document_url", nullable = false, length = 500)
        String url,

        @Column(name = "submitted_at", nullable = false)
        Instant submittedAt
) {
}
