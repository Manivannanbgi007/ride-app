package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The dishes a restaurant sold most in a period (calendar days in the business time zone, both ends included). */
public record RestaurantTopItemsResponse(
        long restaurantId,
        LocalDate from,
        LocalDate to,
        List<Item> items
) {

    /**
     * @param name     the dish name as written on its latest order line
     * @param quantity portions ordered
     * @param orders   distinct orders containing the dish
     * @param revenue  sum of the lines' totals (before coupon discount, delivery fee and tax)
     */
    public record Item(long menuItemId, String name, long quantity, long orders, BigDecimal revenue) {
    }
}
