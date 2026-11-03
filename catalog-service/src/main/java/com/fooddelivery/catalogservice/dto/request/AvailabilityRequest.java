package com.fooddelivery.catalogservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(

        @Schema(description = "false = sold out for now; the item stays on the menu", example = "false")
        @NotNull
        Boolean available
) {
}
