package com.fooddelivery.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentStatus;

/** Payment monitoring for a period (also feeds the admin dashboard later). */
public record PaymentSummaryResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Map<PaymentStatus, Totals> byStatus,
        Map<PaymentMethod, Totals> byMethod,
        BigDecimal collectedAmount,
        BigDecimal refundedAmount,
        String currency
) {

    public record Totals(long count, BigDecimal amount) {
    }
}
