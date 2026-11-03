package com.fooddelivery.userservice.dto.request;

import com.fooddelivery.userservice.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @NotBlank
        String currentPassword,

        @ValidPassword
        String newPassword
) {
}
