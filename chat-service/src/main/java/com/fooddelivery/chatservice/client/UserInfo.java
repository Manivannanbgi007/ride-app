package com.fooddelivery.chatservice.client;

import com.fooddelivery.chatservice.entity.Role;

/** What chat-service needs to know about a user, from user-service's internal API. */
public record UserInfo(Long id, String fullName, String role) {

    public boolean isPlatformAdmin() {
        return Role.PLATFORM_ADMIN.name().equals(role);
    }
}
