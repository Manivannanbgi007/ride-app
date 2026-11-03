package com.fooddelivery.deliveryservice.dto.request;

import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangeAvailabilityRequest(

        @Schema(description = "AVAILABLE (receive deliveries) or OFFLINE", example = "AVAILABLE")
        @NotNull
        PartnerStatus status
) {
}
