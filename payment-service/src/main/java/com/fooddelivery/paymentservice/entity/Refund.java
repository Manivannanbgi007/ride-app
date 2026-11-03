package com.fooddelivery.paymentservice.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.paymentservice.entity.PaymentEnums.RefundStatus;
import com.fooddelivery.paymentservice.entity.PaymentEnums.RequesterType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Same service: a plain id keeps the entity simple (the database has a real foreign key).
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_type", nullable = false, length = 20)
    private RequesterType requestedByType;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        requestedAt = Instant.now();
        updatedAt = requestedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
