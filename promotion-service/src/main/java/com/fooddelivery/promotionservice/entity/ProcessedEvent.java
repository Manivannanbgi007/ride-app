package com.fooddelivery.promotionservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/** A Kafka event this service has already applied (see ProcessedEventGuard). */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    @Override
    public String getId() {
        return eventId;
    }

    /** Always a new row: save() must INSERT, so a duplicate id fails on the primary key instead of being merged. */
    @Override
    public boolean isNew() {
        return true;
    }
}
