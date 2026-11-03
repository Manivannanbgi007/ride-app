package com.fooddelivery.paymentservice.dto.request;

import java.time.LocalDate;

import com.fooddelivery.paymentservice.entity.PaymentEnums.RefundStatus;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

public record RefundSearchCriteria(

        @Parameter(description = "Refunds of one payment")
        Long paymentId,

        @Parameter(description = "Refunds of one order")
        Long orderId,

        @Parameter(description = "REQUESTED, PROCESSING, SUCCESS or FAILED")
        RefundStatus status,

        @Parameter(example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Parameter(example = "2026-10-07")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate
) {
}
