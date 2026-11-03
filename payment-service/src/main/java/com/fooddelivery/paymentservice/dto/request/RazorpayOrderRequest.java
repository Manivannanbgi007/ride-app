package com.fooddelivery.paymentservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Start paying an order with Razorpay Checkout. The amount is never sent: it comes from the order itself. */
public record RazorpayOrderRequest(

        @Schema(description = "The order to pay (it must be waiting for payment)", example = "42")
        @NotNull @Positive
        Long orderId
) {
}
