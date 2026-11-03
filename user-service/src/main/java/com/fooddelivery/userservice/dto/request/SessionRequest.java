package com.fooddelivery.userservice.dto.request;

import com.fooddelivery.userservice.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Refresh or sign out of one area's session. The area is named by its role, because each area keeps its own
 * refresh cookie (customer, owner, partner and admin can be signed in at the same time in one browser).
 */
public record SessionRequest(@NotNull Role role) {
}
