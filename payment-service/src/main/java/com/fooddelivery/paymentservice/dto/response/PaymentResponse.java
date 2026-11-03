package com.fooddelivery.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
import com.fooddelivery.paymentservice.entity.PaymentStatus;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long customerId,
        BigDecimal amount,
        BigDecimal refundedAmount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String instrument,
        String providerReference,
        String failureReason,
        Instant createdAt,
        Instant processingStartedAt,
        Instant completedAt,
        // SIMULATED or RAZORPAY: the gateway that took the payment
        PaymentProvider provider,
        // Razorpay only: the Razorpay order ("order_...")
        String providerOrderId
) {
}
