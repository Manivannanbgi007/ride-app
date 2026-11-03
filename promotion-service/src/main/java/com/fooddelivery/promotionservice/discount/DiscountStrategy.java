package com.fooddelivery.promotionservice.discount;

import java.math.BigDecimal;

import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.DiscountType;

/** Strategy: how one kind of coupon works out its discount. A new kind of discount is one new class. */
public interface DiscountStrategy {

    DiscountType type();

    /** The discount for this items total (two decimals, never more than the items total). */
    BigDecimal discountFor(Coupon coupon, BigDecimal itemsTotal);
}
