package com.fooddelivery.paymentservice.dto.request;

import java.time.LocalDate;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

/** Optional admin filters. Dates are calendar days in the business time zone; toDate is inclusive. */
public record PaymentSearchCriteria(

        @Parameter(description = "Exact payment id")
        Long paymentId,

        @Parameter(description = "Payments of one order")
        Long orderId,

        @Parameter(description = "Payments of one customer")
        Long customerId,

        @Parameter(description = "INITIATED, PROCESSING, SUCCESS, FAILED or REFUNDED (failed payment monitoring: FAILED)")
        PaymentStatus status,

        @Parameter(description = "CARD, UPI or WALLET")
        PaymentMethod method,

        @Parameter(example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Parameter(example = "2026-10-07")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate
) {
}
