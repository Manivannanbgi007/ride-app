package com.fooddelivery.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason for cancelling or rejecting an order. Shown in the order and its timeline. */
public record ReasonRequest(

        @Schema(example = "Ordered by mistake")
        @NotBlank @Size(max = 500)
        String reason
) {
}
