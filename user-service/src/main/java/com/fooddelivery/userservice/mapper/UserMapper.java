package com.fooddelivery.userservice.mapper;

import java.time.Instant;

import com.fooddelivery.userservice.dto.response.AdminUserDetailsResponse;
import com.fooddelivery.userservice.dto.response.AdminUserSummaryResponse;
import com.fooddelivery.userservice.dto.response.InternalUserResponse;
import com.fooddelivery.userservice.dto.response.LoginResponse;
import com.fooddelivery.userservice.dto.response.UserResponse;
import com.fooddelivery.userservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }

    public LoginResponse toLoginResponse(User user, Instant lastLoginAt, String accessToken, long expiresInSeconds) {
        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                lastLoginAt,
                accessToken,
                LoginResponse.TOKEN_TYPE,
                expiresInSeconds);
    }

    public AdminUserSummaryResponse toAdminSummary(User user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }

    public InternalUserResponse toInternalUser(User user) {
        return new InternalUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus());
    }

    public AdminUserDetailsResponse toAdminDetails(User user, long addressCount) {
        return new AdminUserDetailsResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                addressCount);
    }
}
