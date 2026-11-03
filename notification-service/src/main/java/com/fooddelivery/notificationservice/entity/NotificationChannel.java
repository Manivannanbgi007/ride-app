package com.fooddelivery.notificationservice.entity;

/** How a notification reaches its recipient (one sender strategy per channel). */
public enum NotificationChannel {
    /** SMTP email (Mailpit in development). */
    EMAIL,
    /** Text message (simulated: logged). */
    SMS,
    /** Mobile push (simulated: logged) and the user's in-app inbox. */
    PUSH
}
