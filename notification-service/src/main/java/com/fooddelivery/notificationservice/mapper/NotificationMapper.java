package com.fooddelivery.notificationservice.mapper;

import com.fooddelivery.notificationservice.dto.response.AdminNotificationResponse;
import com.fooddelivery.notificationservice.dto.response.NotificationResponse;
import com.fooddelivery.notificationservice.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toInboxEntry(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getSubject(),
                notification.getBody(), notification.getOrderId(), notification.getSentAt(), notification.getReadAt());
    }

    public AdminNotificationResponse toAdminResponse(Notification notification) {
        return new AdminNotificationResponse(notification.getId(), notification.getType(), notification.getChannel(),
                notification.getStatus(), notification.getRecipientUserId(), notification.getRestaurantId(),
                notification.getOrderId(), notification.getRecipientAddress(), notification.getSubject(),
                notification.getBody(), notification.getAttempts(), notification.getNextAttemptAt(),
                notification.getLastError(), notification.getSourceEventId(), notification.getRequestId(),
                notification.getCreatedAt(), notification.getSentAt(), notification.getReadAt());
    }
}
