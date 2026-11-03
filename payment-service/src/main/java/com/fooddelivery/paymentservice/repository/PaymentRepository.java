package com.fooddelivery.paymentservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Optional<Payment> findByIdAndCustomerId(Long id, Long customerId);

    List<Payment> findByCustomerIdAndOrderIdOrderByCreatedAtDesc(Long customerId, Long orderId);

    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    /** At most one per order and status SUCCESS: the active_order_id unique key guarantees it. */
    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    /** The order's running or successful payment, if any (at most one: the active_order_id unique key). */
    Optional<Payment> findFirstByOrderIdAndStatusInOrderByIdDesc(Long orderId, Collection<PaymentStatus> statuses);

    /** Razorpay: the payment a Razorpay order belongs to (provider_order_id is unique). */
    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findByProviderOrderIdAndCustomerId(String providerOrderId, Long customerId);

    /** Payment totals for a period, per status and method, counted in the database. */
    @Query("""
            select p.status as status, p.method as method, count(p) as paymentCount,
                   coalesce(sum(p.amount), 0) as totalAmount, coalesce(sum(p.refundedAmount), 0) as refundedAmount
            from Payment p
            where p.createdAt >= :from and p.createdAt < :before
            group by p.status, p.method
            """)
    List<PaymentTotals> sumByStatusAndMethod(Instant from, Instant before);
}
