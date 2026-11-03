package com.fooddelivery.userservice.dto.response;

import java.time.Instant;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;

/** Full user details for the admin screen. The order summary is added with the Order Service phase. */
public record AdminUserDetailsResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        long addressCount
) {
}
