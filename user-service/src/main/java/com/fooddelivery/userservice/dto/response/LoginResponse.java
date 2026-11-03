package com.fooddelivery.userservice.dto.response;

import java.time.Instant;

import com.fooddelivery.userservice.entity.Role;

/**
 * Result of a successful sign-in or session refresh.
 * <p>
 * Send {@code accessToken} as {@code Authorization: Bearer <accessToken>} on API calls. It is valid for
 * {@code expiresIn} seconds; a new one comes from {@code POST /api/v1/auth/refresh}, which uses the HttpOnly refresh
 * cookie set with this response. The token is the only identity the gateway and the services accept.
 */
public record LoginResponse(
        Long userId,
        String email,
        String fullName,
        Role role,
        Instant lastLoginAt,
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static final String TOKEN_TYPE = "Bearer";

    // Records print every field: never put the token into a log line by accident
    @Override
    public String toString() {
        return "LoginResponse[userId=" + userId + ", role=" + role + ", accessToken=****, expiresIn=" + expiresIn + "]";
    }
}
