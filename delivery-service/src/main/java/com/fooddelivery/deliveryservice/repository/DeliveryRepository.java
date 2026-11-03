package com.fooddelivery.deliveryservice.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    Optional<Delivery> findByOrderId(Long orderId);

    Optional<Delivery> findByIdAndPartnerUserId(Long id, Long partnerUserId);

    Page<Delivery> findByPartnerUserId(Long partnerUserId, Pageable pageable);

    Page<Delivery> findByPartnerUserIdAndStatus(Long partnerUserId, DeliveryStatus status, Pageable pageable);

    /** Deliveries with all their details, waiting for a partner, oldest first (the assignment job's work list). */
    @Query("""
            select d.id from Delivery d
            where d.status = :status
              and d.paymentMethod is not null and d.dropoffAddress.line1 is not null and d.pickupAddress.line1 is not null
            order by d.readyAt, d.id
            """)
    List<Long> findIdsReadyToAssign(DeliveryStatus status, Pageable pageable);

    /** Deliveries whose order or restaurant details have not arrived yet, oldest first. */
    @Query("""
            select d.id from Delivery d
            where d.status = :status
              and (d.paymentMethod is null or d.dropoffAddress.line1 is null or d.pickupAddress.line1 is null)
            order by d.readyAt, d.id
            """)
    List<Long> findIdsMissingDetails(DeliveryStatus status, Pageable pageable);

    /** Offers that were not answered in time. */
    @Query("select d.id from Delivery d where d.status = :status and d.assignedAt < :before order by d.assignedAt")
    List<Long> findIdsAssignedBefore(DeliveryStatus status, Instant before, Pageable pageable);

    /**
     * The delivery, locked until the transaction ends, if it is still in the expected status. A delivery that
     * another instance of the job is working on is skipped (SKIP LOCKED: lock timeout -2) instead of waited for.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select d from Delivery d where d.id = :id and d.status = :status")
    Optional<Delivery> lockInStatus(Long id, DeliveryStatus status);

    /** Totals per status for deliveries created in a period, counted in the database. */
    @Query("""
            select d.status as status, count(d) as total from Delivery d
            where d.createdAt >= :from and d.createdAt < :before
            group by d.status
            """)
    List<DeliveryStatusTotal> countByStatus(Instant from, Instant before);

    /** Average minutes from "food ready" to "delivered" for deliveries completed in a period. */
    @Query(value = """
            SELECT AVG(TIMESTAMPDIFF(SECOND, ready_at, delivered_at)) / 60 FROM deliveries
            WHERE status = 'DELIVERED' AND delivered_at >= :from AND delivered_at < :before
            """, nativeQuery = true)
    BigDecimal averageDeliveryMinutes(Instant from, Instant before);
}
