package com.fooddelivery.userservice.dto.request;

import java.math.BigDecimal;

import com.fooddelivery.userservice.entity.AddressType;
import com.fooddelivery.userservice.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotNull
        AddressType type,

        @NotBlank @Size(max = 255)
        String line1,

        @Size(max = 255)
        String line2,

        @NotBlank @Size(max = 100)
        String city,

        @NotBlank @Size(max = 100)
        String state,

        @NotBlank @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = "must be 3-10 letters, digits, spaces or hyphens")
        String postalCode,

        @NotBlank @Size(max = 100)
        String country,

        @DecimalMin("-90.0") @DecimalMax("90.0")
        BigDecimal latitude,

        @DecimalMin("-180.0") @DecimalMax("180.0")
        BigDecimal longitude,

        @Schema(description = "Make this the default delivery address")
        Boolean makeDefault
) {
}
