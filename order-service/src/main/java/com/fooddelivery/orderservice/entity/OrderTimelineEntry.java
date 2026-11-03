package com.fooddelivery.orderservice.entity;

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

/** One step in an order's history. Append-only, like the audit log. */
@Entity
@Table(name = "order_timeline")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderTimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OrderTimelineEntry(Long orderId, OrderStatus status, ActorType actorType, Long actorUserId,
                              String note, Instant createdAt) {
        this.orderId = orderId;
        this.status = status;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.note = note;
        this.createdAt = createdAt;
    }
}
