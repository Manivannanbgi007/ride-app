package com.fooddelivery.deliveryservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReassignDeliveryRequest(

        @Schema(description = "The partner to give the delivery to (must be AVAILABLE). "
                + "Leave empty to take it from its partner and let the assignment job find someone else.")
        Long partnerUserId,

        @Schema(description = "Why; stored in the audit log and the delivery timeline", example = "Partner reported a breakdown")
        @NotBlank @Size(max = 300)
        String reason
) {
}
