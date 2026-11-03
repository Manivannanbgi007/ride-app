package com.fooddelivery.userservice.dto.response;

import java.time.Instant;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;

/** One row in the admin user list. */
public record AdminUserSummaryResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt
) {
}
