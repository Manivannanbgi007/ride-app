package com.fooddelivery.notificationservice.dto.response;

import java.time.Instant;

import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.entity.NotificationType;

/** Admin view of a notification, with its delivery details. */
public record AdminNotificationResponse(
        Long id,
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,
        Long recipientUserId,
        Long restaurantId,
        Long orderId,
        String recipientAddress,
        String subject,
        String body,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        String sourceEventId,
        String requestId,
        Instant createdAt,
        Instant sentAt,
        Instant readAt
) {
}
