package com.fooddelivery.adminservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(

        @Schema(description = "Why the admin is doing this; stored in the audit log",
                example = "Load the audit records made before Phase 13")
        @NotBlank @Size(max = 500)
        String reason
) {
}
