package com.fooddelivery.notificationservice.entity;

public enum NotificationStatus {
    /** Waiting to be sent, or to be retried. */
    PENDING,
    SENT,
    /** Gave up after the maximum number of attempts: an admin can resend it. */
    FAILED,
    /** Nothing to send it to (for example an SMS for a user without a phone number). */
    SKIPPED
}
