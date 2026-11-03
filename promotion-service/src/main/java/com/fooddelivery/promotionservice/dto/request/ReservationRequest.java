package com.fooddelivery.promotionservice.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Internal (order-service, at checkout): take one use of the coupon for this order.
 *
 * @param reservationKey chosen by order-service, one per checkout (customer + Idempotency-Key): a retried checkout
 *                       gets the same reservation instead of a second use
 */
public record ReservationRequest(
        @NotBlank @Size(max = 150) String reservationKey,
        @NotBlank @Size(max = 30) String code,
        @NotNull Long customerId,
        @NotNull Long restaurantId,
        @NotNull @DecimalMin("0.00") BigDecimal itemsTotal
) {
}
