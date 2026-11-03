package com.fooddelivery.orderservice.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One ordered dish, copied from the catalog at the moment of ordering (later price changes do not affect it). */
@Embeddable
public record OrderLine(

        @Column(name = "menu_item_id", nullable = false)
        Long menuItemId,

        @Column(name = "name", nullable = false, length = 120)
        String name,

        // Plain string: order history must stay readable even if catalog-service changes its enum.
        @Column(name = "food_type", nullable = false, length = 10)
        String foodType,

        @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
        BigDecimal unitPrice,

        @Column(name = "quantity", nullable = false)
        int quantity,

        @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
        BigDecimal lineTotal
) {
}
