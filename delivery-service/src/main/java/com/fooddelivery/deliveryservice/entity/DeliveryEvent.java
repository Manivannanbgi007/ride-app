package com.fooddelivery.deliveryservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** One entry in a delivery's timeline (append only). */
@Entity
@Table(name = "delivery_events")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private DeliveryEventType eventType;

    /** Status of the delivery after the event. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    /** The partner the event is about (for example the one who declined). */
    @Column(name = "partner_user_id")
    private Long partnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DeliveryEvent(Long deliveryId, DeliveryEventType eventType, DeliveryStatus status, Long partnerUserId,
                         ActorType actorType, Long actorUserId, String note, Instant createdAt) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.status = status;
        this.partnerUserId = partnerUserId;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.note = note;
        this.createdAt = createdAt;
    }
}
