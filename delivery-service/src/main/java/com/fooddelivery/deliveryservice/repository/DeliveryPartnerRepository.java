package com.fooddelivery.deliveryservice.repository;

import java.util.List;
import java.util.Optional;

import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long>,
        JpaSpecificationExecutor<DeliveryPartner> {

    /**
     * The next partner for a delivery: available, in the restaurant's city, waiting longest, and not one who
     * declined, ignored or was moved on or off this delivery by an admin. The row stays locked until the
     * transaction ends; partners locked by another assignment are skipped (SKIP LOCKED), so two deliveries
     * never get the same partner.
     */
    @Query(value = """
            SELECT p.* FROM delivery_partners p
            WHERE p.status = 'AVAILABLE' AND p.city = :city
              AND NOT EXISTS (SELECT 1 FROM delivery_events e
                              WHERE e.delivery_id = :deliveryId AND e.partner_user_id = p.user_id
                                AND e.event_type IN ('DECLINED', 'EXPIRED', 'REASSIGNED'))
            ORDER BY p.available_since, p.user_id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<DeliveryPartner> lockNextAvailable(@Param("city") String city, @Param("deliveryId") Long deliveryId);

    /** Partners per status, counted in the database (admin availability overview). */
    @Query("select p.status as status, count(p) as total from DeliveryPartner p group by p.status")
    List<PartnerStatusTotal> countByStatus();

    /**
     * The partner, locked until the transaction ends: news from delivery-partner-service and the partner going
     * online wait for each other. The assignment job skips a locked partner (SKIP LOCKED) for that round.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DeliveryPartner p where p.userId = :userId")
    Optional<DeliveryPartner> lockById(@Param("userId") Long userId);
}
