package com.fooddelivery.restaurantservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

    /** Owner access: another owner's restaurant id simply is not found. */
    Optional<Restaurant> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<Restaurant> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    /** Public access: only restaurants in the given status are visible. */
    Optional<Restaurant> findByIdAndStatus(Long id, RestaurantStatus status);

    /** The next restaurants by id after the given one, with only their id and version (search index check). */
    @Query("select r.id as id, r.version as version from Restaurant r where r.id > :afterId order by r.id")
    List<IdVersion> findVersionsAfter(Long afterId, Pageable pageable);

    /** All restaurants per status (statistics). */
    @Query("select r.status as status, count(r) as total from Restaurant r group by r.status")
    List<RestaurantStatusTotal> countPerStatus();

    /** Restaurants submitted in a period, per status they are in now. */
    @Query("""
            select r.status as status, count(r) as total from Restaurant r
            where r.createdAt >= :from and r.createdAt < :before
            group by r.status
            """)
    List<RestaurantStatusTotal> countSubmittedPerStatus(Instant from, Instant before);

    /**
     * Restaurants submitted per calendar day. The UTC timestamps are shifted by the business time zone's UTC offset
     * (e.g. '+05:30') before taking the date; the caller passes a range in which that offset does not change.
     * Uses idx_restaurants_created.
     */
    @Query(value = """
            SELECT DATE_FORMAT(CONVERT_TZ(created_at, '+00:00', :utcOffset), '%Y-%m-%d') AS day, COUNT(*) AS total
            FROM restaurants
            WHERE created_at >= :from AND created_at < :before
            GROUP BY day
            """, nativeQuery = true)
    List<RegistrationDayTotal> countSubmittedPerDay(Instant from, Instant before, String utcOffset);
}
