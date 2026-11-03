package com.fooddelivery.promotionservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

/**
 * A coupon. {@code timesUsed} is changed only by the reservation logic, with update statements that do not touch
 * the version (see CouponRepository): an admin editing a popular coupon is not interrupted by every checkout.
 */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "per_customer_limit")
    private Integer perCustomerLimit;

    @Column(name = "times_used", nullable = false, insertable = false, updatable = false)
    private int timesUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** Whether the coupon can be used on {@code today} (the business day), ignoring per-customer limits. */
    public CouponAvailability availabilityOn(LocalDate today) {
        if (status == CouponStatus.INACTIVE) {
            return CouponAvailability.INACTIVE;
        }
        if (today.isBefore(validFrom)) {
            return CouponAvailability.SCHEDULED;
        }
        if (today.isAfter(validUntil)) {
            return CouponAvailability.EXPIRED;
        }
        if (usageLimit != null && timesUsed >= usageLimit) {
            return CouponAvailability.USED_UP;
        }
        return CouponAvailability.AVAILABLE;
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
