package com.fooddelivery.orderservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One menu item in a cart. Name and price are not stored: they are always read fresh from catalog-service. */
@Embeddable
public record CartLine(

        @Column(name = "menu_item_id", nullable = false)
        Long menuItemId,

        @Column(name = "quantity", nullable = false)
        int quantity,

        @Column(name = "added_at", nullable = false)
        Instant addedAt
) {
}
