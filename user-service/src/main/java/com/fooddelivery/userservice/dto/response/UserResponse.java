package com.fooddelivery.userservice.dto.response;

import java.time.Instant;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;

/** A user's own profile. Never contains the password hash. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt
) {
}
