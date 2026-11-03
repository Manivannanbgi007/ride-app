package com.fooddelivery.promotionservice.entity;

import java.math.BigDecimal;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One use of a coupon (see RedemptionStatus for its life). */
@Entity
@Table(name = "coupon_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false, updatable = false)
    private Long couponId;

    @Column(name = "coupon_code", nullable = false, length = 30, updatable = false)
    private String couponCode;

    @Column(name = "reservation_key", nullable = false, length = 150, updatable = false)
    private String reservationKey;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RedemptionStatus status;

    @Column(name = "release_reason", length = 255)
    private String releaseReason;

    @Column(name = "reserved_at", nullable = false, updatable = false)
    private Instant reservedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Version
    private Long version;
}
