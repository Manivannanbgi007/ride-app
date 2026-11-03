package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;

/**
 * @param itemsTotal     the items at their prices
 * @param discountAmount coupon discount taken off the items (0 without a coupon)
 * @param totalAmount    what the customer pays: items - discount + delivery fee + tax
 */
public record PriceBreakdown(
        BigDecimal itemsTotal,
        BigDecimal discountAmount,
        BigDecimal deliveryFee,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency
) {
}
