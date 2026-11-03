package com.fooddelivery.promotionservice.dto.response;

import java.math.BigDecimal;
import java.util.Map;

import com.fooddelivery.promotionservice.entity.RedemptionStatus;

/**
 * A coupon with its usage (usage monitoring).
 *
 * @param usesByStatus  how many uses are reserved, on an order, redeemed, released (order cancelled) or expired
 * @param discountGiven discount on orders that were placed and not cancelled (on an order or redeemed)
 * @param customers     different customers who have used it at least once (any status)
 */
public record CouponDetailsResponse(
        CouponResponse coupon,
        Map<RedemptionStatus, Long> usesByStatus,
        BigDecimal discountGiven,
        long customers
) {
}
