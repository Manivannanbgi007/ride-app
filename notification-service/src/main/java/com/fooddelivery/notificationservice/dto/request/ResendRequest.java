package com.fooddelivery.notificationservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why an admin sends a notification again (kept in the audit log). */
public record ResendRequest(@NotBlank @Size(max = 500) String reason) {
}
