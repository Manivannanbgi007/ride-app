package com.fooddelivery.deliverypartnerservice.dto.request;

import com.fooddelivery.deliverypartnerservice.entity.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a partner fills in before going online: deliveries are offered in their city. */
public record UpdatePartnerProfileRequest(

        @Schema(example = "MOTORCYCLE")
        @NotNull
        VehicleType vehicleType,

        @Schema(example = "KA01AB1234")
        @NotBlank @Size(max = 20)
        String vehicleNumber,

        @Schema(description = "Deliveries are offered from restaurants in this city", example = "Bengaluru")
        @NotBlank @Size(max = 100)
        String city
) {
}
