package com.fooddelivery.promotionservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fooddelivery.promotionservice.entity.CouponAvailability;
import com.fooddelivery.promotionservice.entity.CouponStatus;
import com.fooddelivery.promotionservice.entity.DiscountType;

/**
 * A coupon as admins see it.
 *
 * @param summary      the discount in words ("20% off, up to 150.00")
 * @param timesUsed    uses counting against the limit right now (reserved, on an order, or redeemed)
 * @param availability whether it can be used today (status, dates and uses together)
 */
public record CouponResponse(
        Long id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        BigDecimal minOrderAmount,
        String summary,
        Long restaurantId,
        LocalDate validFrom,
        LocalDate validUntil,
        Integer usageLimit,
        Integer perCustomerLimit,
        int timesUsed,
        CouponStatus status,
        CouponAvailability availability,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
