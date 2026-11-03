package com.fooddelivery.paymentservice.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
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
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Sum of refunds that succeeded or are still running (reserved, so the payment can never be over-refunded). */
    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    /** The gateway that took (or is taking) the payment; its refunds go back through the same one. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider = PaymentProvider.SIMULATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Masked, for example "VISA **** 1111". Card numbers are never stored or logged. */
    @Column(length = 60)
    private String instrument;

    /** The gateway's id of the money movement (Razorpay: the payment id "pay_..."), set once it succeeded. */
    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    /** Razorpay only: the Razorpay order the customer pays in Checkout ("order_..."); unique. */
    @Column(name = "provider_order_id", length = 64)
    private String providerOrderId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public BigDecimal refundableAmount() {
        return amount.subtract(refundedAmount);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
