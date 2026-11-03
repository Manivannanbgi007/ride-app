package com.fooddelivery.paymentservice.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequest(

        @Schema(description = "Amount to refund; leave empty to refund everything not refunded yet", example = "100.00")
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal amount,

        @Schema(example = "Order cancelled by the restaurant")
        @NotBlank @Size(max = 500)
        String reason
) {
}
