package com.fooddelivery.userservice.dto.request;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.validation.ValidPassword;
import com.fooddelivery.userservice.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        @ValidPassword
        String password,

        @NotBlank @Size(max = 100)
        String fullName,

        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,

        @Schema(description = "CUSTOMER (default), RESTAURANT_ADMIN or DELIVERY_PARTNER. PLATFORM_ADMIN is not allowed.")
        Role role
) {
}
