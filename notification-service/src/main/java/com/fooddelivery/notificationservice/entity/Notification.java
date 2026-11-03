package com.fooddelivery.notificationservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One notification to one recipient on one channel. See V1__create_notifications_table.sql. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_event_id", nullable = false, updatable = false, length = 36)
    private String sourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "restaurant_id", updatable = false)
    private Long restaurantId;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(name = "recipient_address")
    private String recipientAddress;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "request_id", updatable = false, length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Version
    private Long version;

    /** A new notification, ready to be sent as soon as the dispatcher runs. */
    @SuppressWarnings({"checkstyle:ParameterNumber", "java:S107"}) // every field of a new notification
    public static Notification pending(String sourceEventId, NotificationType type, NotificationChannel channel,
                                       Long recipientUserId, Long restaurantId, Long orderId, String recipientAddress,
                                       String subject, String body, String requestId, Instant now) {
        Notification notification = new Notification();
        notification.sourceEventId = sourceEventId;
        notification.type = type;
        notification.channel = channel;
        notification.recipientUserId = recipientUserId;
        notification.restaurantId = restaurantId;
        notification.orderId = orderId;
        notification.recipientAddress = recipientAddress;
        notification.subject = subject;
        notification.body = body;
        notification.requestId = requestId;
        notification.status = NotificationStatus.PENDING;
        notification.nextAttemptAt = now;
        notification.createdAt = now;
        return notification;
    }

    public void markSent(String address, Instant now) {
        this.recipientAddress = address;
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markSkipped(String reason) {
        this.status = NotificationStatus.SKIPPED;
        this.nextAttemptAt = null;
        this.lastError = truncate(reason);
    }

    public void scheduleRetry(Instant nextAttemptAt, String error) {
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(error);
    }

    public void markFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.nextAttemptAt = null;
        this.lastError = truncate(error);
    }

    /** Admin resend: a fresh start with all attempts available again. */
    public void resend(Instant now) {
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.lastError = null;
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
