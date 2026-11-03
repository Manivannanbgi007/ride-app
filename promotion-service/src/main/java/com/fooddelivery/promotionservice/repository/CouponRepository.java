package com.fooddelivery.promotionservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.CouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CouponRepository extends JpaRepository<Coupon, Long>, JpaSpecificationExecutor<Coupon> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * The coupon, locked until the transaction ends: reservations of one coupon run one after the other, so two
     * customers can never both take its last use.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.code = :code")
    Optional<Coupon> lockByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> lockById(Long id);

    /** One more use. An update statement: the coupon's version (admin edits) is not touched. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Coupon c set c.timesUsed = c.timesUsed + 1 where c.id = :id")
    void addUse(Long id);

    /** One use given back. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Coupon c set c.timesUsed = c.timesUsed - 1 where c.id = :id and c.timesUsed > 0")
    void removeUse(Long id);

    /** Coupons customers may see on a day: active and within their dates (used-up ones are filtered by the caller). */
    @Query("""
            select c from Coupon c
            where c.status = :status and c.validFrom <= :day and c.validUntil >= :day
              and (c.restaurantId is null or c.restaurantId = :restaurantId)
            order by c.validUntil, c.code
            """)
    List<Coupon> findValidOn(CouponStatus status, LocalDate day, Long restaurantId);
}
