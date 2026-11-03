package com.fooddelivery.promotionservice.dto.response;

import java.math.BigDecimal;

/**
 * Internal: the answer to "would this coupon work for this cart".
 *
 * @param applicable     false with a reason a customer can read ("Add items worth 120.00 more ...")
 * @param discountAmount the discount it would give now (0 when not applicable)
 */
public record CouponCheckResponse(
        String code,
        boolean applicable,
        BigDecimal discountAmount,
        String reason,
        String description
) {
}
