package com.fooddelivery.promotionservice.entity;

/** How a coupon takes money off the items total. */
public enum DiscountType {
    /** discountValue percent of the items total, at most maxDiscount when set. */
    PERCENTAGE,
    /** discountValue off, never more than the items total. */
    FIXED
}
