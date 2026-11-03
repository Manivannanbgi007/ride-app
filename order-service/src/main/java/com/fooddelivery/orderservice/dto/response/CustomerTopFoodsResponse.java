package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The dishes one customer ordered most in a window; image and category come from catalog-service when it answers. */
public record CustomerTopFoodsResponse(
        long customerId,
        String period,
        LocalDate from,
        List<Item> items
) {

    /**
     * @param orders    distinct orders containing the dish
     * @param unitPrice the dish's price in the customer's latest order of it in the window
     */
    public record Item(long menuItemId, String name, long orders, long quantity, BigDecimal totalSpent,
                       BigDecimal unitPrice, Instant lastOrderedAt, String imageUrl, String categoryName) {
    }
}
