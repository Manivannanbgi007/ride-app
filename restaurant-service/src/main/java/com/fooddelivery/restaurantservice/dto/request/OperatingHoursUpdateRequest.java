package com.fooddelivery.restaurantservice.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the whole weekly schedule. */
public record OperatingHoursUpdateRequest(

        @Schema(description = "The version you last read (optimistic locking)")
        @NotNull
        Long version,

        @NotEmpty @Size(max = 21)
        List<@NotNull @Valid OperatingHoursRequest> operatingHours
) {
}
