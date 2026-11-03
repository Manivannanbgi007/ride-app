package com.fooddelivery.promotionservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.promotionservice.entity.RedemptionStatus;

/** One use of a coupon, for admins. */
public record RedemptionResponse(
        Long id,
        String couponCode,
        Long customerId,
        Long restaurantId,
        Long orderId,
        BigDecimal orderAmount,
        BigDecimal discountAmount,
        RedemptionStatus status,
        String releaseReason,
        Instant reservedAt,
        Instant appliedAt,
        Instant redeemedAt,
        Instant releasedAt
) {
}
