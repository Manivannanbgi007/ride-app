package com.fooddelivery.restaurantservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An event waiting in the transactional outbox (or already published, kept for a while for tracing).
 * Written by {@link com.fooddelivery.restaurantservice.kafka.producer.OutboxWriter} in the business transaction,
 * published by {@link com.fooddelivery.restaurantservice.kafka.producer.OutboxRelay}.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 150)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(nullable = false, updatable = false, length = 200)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, updatable = false, columnDefinition = "mediumblob")
    private byte[] payload;

    @Column(name = "avro_schema", nullable = false, updatable = false, columnDefinition = "text")
    private String avroSchema;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    /** W3C traceparent of the request or event that wrote this event: the relay sends it in that same trace. */
    @Column(name = "trace_parent", length = 55, updatable = false)
    private String traceParent;

    public static OutboxEvent create(String eventId, String eventType, String aggregateType, Long aggregateId,
                                     String topic, String messageKey, byte[] payload, String avroSchema,
                                     Instant createdAt, String traceParent) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.topic = topic;
        event.messageKey = messageKey;
        event.payload = payload;
        event.avroSchema = avroSchema;
        event.createdAt = createdAt;
        event.traceParent = traceParent;
        return event;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.attempts++;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    /** The short name of the event, for logs (e.g. UserRegistered). */
    public String eventName() {
        return eventType.substring(eventType.lastIndexOf('.') + 1);
    }
}
