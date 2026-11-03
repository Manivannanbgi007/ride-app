package com.fooddelivery.orderservice.dto.request;

import com.fooddelivery.orderservice.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Checkout: the items come from the customer's cart. */
public record PlaceOrderRequest(

        @Schema(description = "Id of one of the customer's saved addresses (user-service)")
        @NotNull
        Long addressId,

        @Schema(description = "CASH_ON_DELIVERY goes straight to the restaurant; online methods wait for payment (Phase 6)")
        @NotNull
        PaymentMethod paymentMethod,

        @Schema(description = "Instructions for the restaurant or rider", example = "Less spicy, please ring the bell")
        @Size(max = 500)
        String notes
) {
}
