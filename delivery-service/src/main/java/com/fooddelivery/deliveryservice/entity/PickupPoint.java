package com.fooddelivery.deliveryservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Where a restaurant's orders are picked up: delivery-service's copy of the restaurant's name and address
 * (restaurant-service owns them). Written only through PickupPointRepository.upsertIfNewer.
 */
@Entity
@Table(name = "pickup_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickupPoint {

    @Id
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Embedded
    private Address address;

    /** The restaurant's version in restaurant-service. */
    @Column(name = "source_version", nullable = false)
    private Long sourceVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
