package com.fooddelivery.notificationservice.dto.request;

import java.time.LocalDate;

import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.entity.NotificationType;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

/** Admin search filters; all optional. */
public record NotificationSearchCriteria(

        @Parameter(description = "Notifications of one user")
        Long recipientUserId,

        @Parameter(description = "Notifications about one order")
        Long orderId,

        @Parameter(description = "Notifications to one restaurant")
        Long restaurantId,

        @Parameter(description = "For example ORDER_PLACED or WELCOME")
        NotificationType type,

        @Parameter(description = "EMAIL, SMS or PUSH")
        NotificationChannel channel,

        @Parameter(description = "PENDING, SENT, FAILED or SKIPPED (failed notifications: FAILED)")
        NotificationStatus status,

        @Parameter(example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Parameter(example = "2026-10-07")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate
) {
}
