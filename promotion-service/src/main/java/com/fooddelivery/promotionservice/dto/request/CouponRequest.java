package com.fooddelivery.promotionservice.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fooddelivery.promotionservice.entity.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A new coupon. It is ACTIVE at once; deactivate it to pause it. */
public record CouponRequest(

        @Schema(description = "What customers type (stored in capitals)", example = "WELCOME100")
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,30}", message = "3 to 30 letters, digits, - or _")
        String code,

        @Schema(example = "100 off your first order")
        @Size(max = 255)
        String description,

        @NotNull
        DiscountType discountType,

        @Schema(description = "PERCENTAGE: the percent (1-100). FIXED: the amount.", example = "100.00")
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal discountValue,

        @Schema(description = "PERCENTAGE only: the most it takes off", example = "150.00")
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal maxDiscount,

        @Schema(description = "Items total the order must reach (default 0)", example = "499.00")
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal minOrderAmount,

        @Schema(description = "Only this restaurant's orders; empty for every restaurant")
        Long restaurantId,

        @Schema(example = "2026-10-01")
        @NotNull
        LocalDate validFrom,

        @Schema(description = "Last day it can be used (inclusive)", example = "2026-10-31")
        @NotNull
        LocalDate validUntil,

        @Schema(description = "Total uses; empty for unlimited", example = "10000")
        @Min(1)
        Integer usageLimit,

        @Schema(description = "Uses per customer; empty for unlimited", example = "1")
        @Min(1)
        Integer perCustomerLimit
) {
}
