package com.fooddelivery.promotionservice.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Internal (order-service): would this coupon work for this cart, and for how much?
 *
 * @param itemsTotal the items total before discount, at current prices
 */
public record CouponCheckRequest(
        @NotBlank @Size(max = 30) String code,
        @NotNull Long customerId,
        @NotNull Long restaurantId,
        @NotNull @DecimalMin("0.00") BigDecimal itemsTotal
) {
}
