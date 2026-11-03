package com.fooddelivery.deliveryservice.repository;

import java.math.BigDecimal;

import com.fooddelivery.deliveryservice.entity.PickupPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {

    /**
     * Inserts the copy, or updates it only if {@code sourceVersion} is newer than the stored one, in one
     * atomic statement: an old or repeated event (or a slower REST answer) never overwrites newer data.
     * <p>
     * MySQL applies the assignments from left to right, so source_version is updated last:
     * the conditions before it must compare with the old version.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO pickup_points (restaurant_id, name, line1, line2, city, state, postal_code, country,
                                       latitude, longitude, source_version, updated_at)
            VALUES (:restaurantId, :name, :line1, :line2, :city, :state, :postalCode, :country,
                    :latitude, :longitude, :sourceVersion, UTC_TIMESTAMP(6)) AS incoming
            ON DUPLICATE KEY UPDATE
                name = IF(incoming.source_version > pickup_points.source_version, incoming.name, pickup_points.name),
                line1 = IF(incoming.source_version > pickup_points.source_version, incoming.line1, pickup_points.line1),
                line2 = IF(incoming.source_version > pickup_points.source_version, incoming.line2, pickup_points.line2),
                city = IF(incoming.source_version > pickup_points.source_version, incoming.city, pickup_points.city),
                state = IF(incoming.source_version > pickup_points.source_version, incoming.state, pickup_points.state),
                postal_code = IF(incoming.source_version > pickup_points.source_version,
                                 incoming.postal_code, pickup_points.postal_code),
                country = IF(incoming.source_version > pickup_points.source_version,
                             incoming.country, pickup_points.country),
                latitude = IF(incoming.source_version > pickup_points.source_version,
                              incoming.latitude, pickup_points.latitude),
                longitude = IF(incoming.source_version > pickup_points.source_version,
                               incoming.longitude, pickup_points.longitude),
                updated_at = IF(incoming.source_version > pickup_points.source_version,
                                incoming.updated_at, pickup_points.updated_at),
                source_version = GREATEST(pickup_points.source_version, incoming.source_version)
            """, nativeQuery = true)
    @SuppressWarnings({"checkstyle:ParameterNumber", "java:S107"}) // one parameter per column of the upsert
    void upsertIfNewer(@Param("restaurantId") Long restaurantId,
                       @Param("name") String name,
                       @Param("line1") String line1,
                       @Param("line2") String line2,
                       @Param("city") String city,
                       @Param("state") String state,
                       @Param("postalCode") String postalCode,
                       @Param("country") String country,
                       @Param("latitude") BigDecimal latitude,
                       @Param("longitude") BigDecimal longitude,
                       @Param("sourceVersion") Long sourceVersion);
}
