package com.fooddelivery.catalogservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(

        @Schema(description = "Why the admin is doing this; stored in the audit log", example = "Seasonal category")
        @NotBlank @Size(max = 500)
        String reason
) {
}
