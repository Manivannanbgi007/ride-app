package com.fooddelivery.catalogservice.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record PriceUpdateRequest(

        @Schema(description = "New price in INR", example = "319.00")
        @NotNull @DecimalMin("1.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2)
        BigDecimal price,

        @Schema(description = "The version you last read (optimistic locking)")
        @NotNull
        Long version
) {
}
