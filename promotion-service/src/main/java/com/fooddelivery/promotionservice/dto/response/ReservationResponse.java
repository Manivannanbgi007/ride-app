package com.fooddelivery.promotionservice.dto.response;

import java.math.BigDecimal;

import com.fooddelivery.promotionservice.entity.RedemptionStatus;

/** Internal: the coupon use taken for a checkout (order-service stores the id with the order). */
public record ReservationResponse(Long reservationId, String code, BigDecimal discountAmount, RedemptionStatus status) {
}
