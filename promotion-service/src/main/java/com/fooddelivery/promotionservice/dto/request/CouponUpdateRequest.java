package com.fooddelivery.promotionservice.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fooddelivery.promotionservice.entity.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Changes to a coupon (the code cannot change: customers may already have it). Orders placed before keep the
 * discount they got.
 */
public record CouponUpdateRequest(

        @Size(max = 255)
        String description,

        @NotNull
        DiscountType discountType,

        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal discountValue,

        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal maxDiscount,

        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal minOrderAmount,

        Long restaurantId,

        @NotNull
        LocalDate validFrom,

        @NotNull
        LocalDate validUntil,

        @Min(1)
        Integer usageLimit,

        @Min(1)
        Integer perCustomerLimit,

        @Schema(description = "The version you last read (optimistic locking)")
        @NotNull
        Long version
) {
}
