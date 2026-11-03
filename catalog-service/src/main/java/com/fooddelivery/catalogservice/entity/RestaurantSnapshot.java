package com.fooddelivery.catalogservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * catalog-service's copy of a restaurant, owned by restaurant-service.
 * Read-only through JPA: it is written only by
 * {@link com.fooddelivery.catalogservice.repository.RestaurantSnapshotRepository#upsertIfNewer}.
 */
@Entity
@Table(name = "restaurant_snapshots")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RestaurantSnapshot {

    @Id
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 150)
    private String name;

    /** Plain string, like the event: new statuses in restaurant-service must not break this service. */
    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "source_version", nullable = false)
    private Long sourceVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
