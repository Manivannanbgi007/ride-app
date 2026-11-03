package com.fooddelivery.catalogservice.repository;

import com.fooddelivery.catalogservice.entity.RestaurantSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RestaurantSnapshotRepository extends JpaRepository<RestaurantSnapshot, Long> {

    /**
     * Inserts the copy, or updates it only if {@code sourceVersion} is newer than the stored one,
     * in one atomic statement: an old or repeated event (or a slower REST answer) never overwrites newer data.
     * <p>
     * MySQL applies the assignments from left to right, so source_version is updated last:
     * the conditions before it must compare with the old version.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO restaurant_snapshots (restaurant_id, owner_user_id, name, status, source_version, updated_at)
            VALUES (:restaurantId, :ownerUserId, :name, :status, :sourceVersion, UTC_TIMESTAMP(6)) AS incoming
            ON DUPLICATE KEY UPDATE
                owner_user_id = IF(incoming.source_version > restaurant_snapshots.source_version,
                                   incoming.owner_user_id, restaurant_snapshots.owner_user_id),
                name = IF(incoming.source_version > restaurant_snapshots.source_version,
                          incoming.name, restaurant_snapshots.name),
                status = IF(incoming.source_version > restaurant_snapshots.source_version,
                            incoming.status, restaurant_snapshots.status),
                updated_at = IF(incoming.source_version > restaurant_snapshots.source_version,
                                incoming.updated_at, restaurant_snapshots.updated_at),
                source_version = GREATEST(restaurant_snapshots.source_version, incoming.source_version)
            """, nativeQuery = true)
    void upsertIfNewer(@Param("restaurantId") Long restaurantId,
                       @Param("ownerUserId") Long ownerUserId,
                       @Param("name") String name,
                       @Param("status") String status,
                       @Param("sourceVersion") Long sourceVersion);
}
