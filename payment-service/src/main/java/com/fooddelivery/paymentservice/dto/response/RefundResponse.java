package com.fooddelivery.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.paymentservice.entity.PaymentEnums.RefundStatus;
import com.fooddelivery.paymentservice.entity.PaymentEnums.RequesterType;

/** Everything the admin refund screen shows: who asked, how much, why, and when it finished. */
public record RefundResponse(
        Long id,
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        RequesterType requestedByType,
        Long requestedByUserId,
        String providerReference,
        String failureReason,
        Instant requestedAt,
        Instant completedAt
) {
}
