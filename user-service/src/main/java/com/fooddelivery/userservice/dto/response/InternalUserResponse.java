package com.fooddelivery.userservice.dto.response;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;

/** Basic user data for other microservices (for example the restaurant owner shown to admins). */
public record InternalUserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        UserStatus status
) {
}
