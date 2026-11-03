package com.fooddelivery.notificationservice.repository;

import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;

/** One row of the notification summary query. */
public interface NotificationTotals {

    NotificationStatus getStatus();

    NotificationChannel getChannel();

    long getTotal();
}
