package com.fooddelivery.paymentservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.paymentservice.entity.PaymentEnums.RefundStatus;
import com.fooddelivery.paymentservice.entity.Refund;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RefundRepository extends JpaRepository<Refund, Long>, JpaSpecificationExecutor<Refund> {

    Optional<Refund> findByPaymentIdAndIdempotencyKey(Long paymentId, String idempotencyKey);

    List<Refund> findByPaymentIdOrderByRequestedAtDesc(Long paymentId);

    /** Refunds left in a status since before a time, oldest first (refund recovery). */
    @Query("select r.id from Refund r where r.status = :status and r.requestedAt < :before order by r.requestedAt")
    List<Long> findIdsByStatusRequestedBefore(RefundStatus status, Instant before, Pageable pageable);
}
