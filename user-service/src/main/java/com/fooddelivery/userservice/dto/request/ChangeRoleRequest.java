package com.fooddelivery.userservice.dto.request;

import com.fooddelivery.userservice.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeRoleRequest(

        @NotNull
        Role role,

        @Schema(description = "Why the role is changed; stored in the audit log", example = "Approved as restaurant owner")
        @NotBlank @Size(max = 500)
        String reason
) {
}
