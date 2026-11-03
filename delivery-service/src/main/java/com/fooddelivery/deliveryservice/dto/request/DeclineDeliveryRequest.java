package com.fooddelivery.deliveryservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record DeclineDeliveryRequest(

        @Schema(description = "Optional, shown in the delivery timeline", example = "Flat tyre")
        @Size(max = 300)
        String reason
) {
}
