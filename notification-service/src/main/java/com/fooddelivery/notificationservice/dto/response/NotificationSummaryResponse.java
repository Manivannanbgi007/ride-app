package com.fooddelivery.notificationservice.dto.response;

import java.time.LocalDate;
import java.util.Map;

import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;

/** Admin dashboard numbers for a period. */
public record NotificationSummaryResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long total,
        Map<NotificationStatus, Long> byStatus,
        Map<NotificationChannel, Long> byChannel
) {
}
