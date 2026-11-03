package com.fooddelivery.deliveryservice.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record CompleteDeliveryRequest(

        @Schema(description = "Cash on delivery only: the amount collected (must be the order total)", example = "353.95")
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal cashCollected
) {
}
