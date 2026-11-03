package com.fooddelivery.promotionservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.promotionservice.entity.CouponRedemption;
import com.fooddelivery.promotionservice.entity.RedemptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    Optional<CouponRedemption> findByReservationKey(String reservationKey);

    Optional<CouponRedemption> findByOrderId(Long orderId);

    long countByCouponIdAndCustomerIdAndStatusIn(Long couponId, Long customerId, Collection<RedemptionStatus> statuses);

    Page<CouponRedemption> findByCouponId(Long couponId, Pageable pageable);

    Page<CouponRedemption> findByCouponIdAndStatus(Long couponId, RedemptionStatus status, Pageable pageable);

    /** Reservations no order was placed with (the clean-up job's work list). */
    @Query("select r.id from CouponRedemption r where r.status = :status and r.reservedAt < :before order by r.reservedAt")
    List<Long> findIdsReservedBefore(RedemptionStatus status, Instant before, Pageable pageable);

    /** Uses of a coupon per status, with the discount given, counted in the database (usage monitoring). */
    @Query("""
            select r.status as status, count(r) as uses, coalesce(sum(r.discountAmount), 0) as discount
            from CouponRedemption r where r.couponId = :couponId group by r.status
            """)
    List<RedemptionTotals> totalsForCoupon(Long couponId);

    /** Different customers who used the coupon at least once (any status). */
    @Query("select count(distinct r.customerId) from CouponRedemption r where r.couponId = :couponId")
    long countDistinctCustomers(Long couponId);
}
