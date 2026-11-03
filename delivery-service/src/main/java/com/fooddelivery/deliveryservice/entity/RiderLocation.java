package com.fooddelivery.deliveryservice.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The latest GPS position of the partner on a delivery that is on its way. One row per delivery, replaced by every new
 * position; deleted once the delivery is finished. No version column on purpose: positions are replaced, never merged.
 */
@Entity
@Table(name = "rider_locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiderLocation {

    @Id
    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "partner_user_id", nullable = false)
    private Long partnerUserId;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters")
    private Integer accuracyMeters;

    @Column(name = "heading_deg")
    private Short headingDeg;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public RiderLocation(Long deliveryId, Long partnerUserId) {
        this.deliveryId = deliveryId;
        this.partnerUserId = partnerUserId;
    }

    /** Replaces the position with a newer one. */
    public void moveTo(BigDecimal latitude, BigDecimal longitude, Integer accuracyMeters, Short headingDeg,
                       Instant receivedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.headingDeg = headingDeg;
        this.receivedAt = receivedAt;
    }

    /** True when the stored position was received less than {@code interval} ago (a new one is not needed yet). */
    public boolean isNewerThan(Duration interval, Instant now) {
        return receivedAt != null && receivedAt.plus(interval).isAfter(now);
    }
}
