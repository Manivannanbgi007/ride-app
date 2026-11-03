package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart with current prices from catalog-service.
 *
 * @param coupon    the coupon the customer applied, or null
 * @param orderable false when something blocks checkout (see problems)
 */
public record CartResponse(
        Long restaurantId,
        List<Item> items,
        Coupon coupon,
        PriceBreakdown price,
        boolean orderable,
        List<String> problems
) {

    public record Item(
            Long menuItemId,
            String name,
            String foodType,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            boolean orderable
    ) {
    }

    /**
     * @param applied        true when the discount is in the price; false with the reason (it then blocks checkout:
     *                       remove the coupon or change the cart)
     * @param discountAmount the discount it gives now (0 when not applied)
     */
    public record Coupon(
            String code,
            String description,
            boolean applied,
            BigDecimal discountAmount,
            String reason
    ) {
    }
}
