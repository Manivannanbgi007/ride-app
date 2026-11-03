package com.fooddelivery.promotionservice.repository;

import java.math.BigDecimal;

import com.fooddelivery.promotionservice.entity.RedemptionStatus;

/** One row of {@link CouponRedemptionRepository#totalsForCoupon}. */
public interface RedemptionTotals {

    RedemptionStatus getStatus();

    long getUses();

    BigDecimal getDiscount();
}
