package com.fooddelivery.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(

        @Schema(description = "New quantity of this item in the cart", example = "2")
        @NotNull @Min(1) @Max(99)
        Integer quantity
) {
}
