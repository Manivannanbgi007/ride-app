package com.fooddelivery.restaurantservice.dto.request;

import java.util.List;

import com.fooddelivery.restaurantservice.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Restaurant submission (POST) and full update (PUT, with version). */
public record RestaurantRequest(

        @NotBlank @Size(max = 150)
        String name,

        @Size(max = 1000)
        String description,

        @Schema(example = "[\"North Indian\", \"Biryani\"]")
        @NotEmpty @Size(max = 5)
        List<@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z][A-Za-z &'-]*$",
                message = "may contain only letters, spaces, &, ' and -") String> cuisines,

        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotNull @Valid
        AddressRequest address,

        @Schema(description = "IANA time zone of the restaurant; defaults to Asia/Kolkata", example = "Asia/Kolkata")
        @Size(max = 50)
        String timeZone,

        @NotBlank @Size(max = 200)
        String legalBusinessName,

        @Schema(description = "GSTIN or other tax registration number")
        @Size(max = 50)
        String taxRegistrationNumber,

        @Schema(description = "Food safety license number (FSSAI in India)")
        @NotBlank @Size(max = 50)
        String foodLicenseNumber,

        @Schema(description = "Must include a FOOD_LICENSE document")
        @NotEmpty @Size(max = 10)
        List<@NotNull @Valid DocumentRequest> documents,

        @NotEmpty @Size(max = 21)
        List<@NotNull @Valid OperatingHoursRequest> operatingHours,

        @Schema(description = "Required when updating: the version you last read (optimistic locking)")
        Long version
) {
}
