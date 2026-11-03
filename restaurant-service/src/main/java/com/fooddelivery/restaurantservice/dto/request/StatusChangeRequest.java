package com.fooddelivery.restaurantservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(

        @Schema(description = "Why the admin is doing this; stored in the audit log and shown to the owner",
                example = "Food license verified")
        @NotBlank @Size(max = 500)
        String reason
) {
}
