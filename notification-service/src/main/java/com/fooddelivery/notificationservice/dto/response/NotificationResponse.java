package com.fooddelivery.notificationservice.dto.response;

import java.time.Instant;

import com.fooddelivery.notificationservice.entity.NotificationType;

/** An entry of a user's in-app inbox. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long orderId,
        Instant sentAt,
        Instant readAt
) {
}
