package com.fooddelivery.promotionservice.repository;

import java.time.LocalDate;

import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.CouponStatus;
import com.fooddelivery.promotionservice.entity.DiscountType;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks of the admin coupon search. */
public final class CouponSpecifications {

    private CouponSpecifications() {
    }

    /** Part of the code, any letter case. */
    public static Specification<Coupon> codeContains(String text) {
        String pattern = "%" + text.toUpperCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(root.get("code"), pattern, '\\');
    }

    public static Specification<Coupon> hasStatus(CouponStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Coupon> hasDiscountType(DiscountType type) {
        return (root, query, cb) -> cb.equal(root.get("discountType"), type);
    }

    public static Specification<Coupon> forRestaurant(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
    }

    /** Valid on that day according to its dates (whatever its status). */
    public static Specification<Coupon> validOn(LocalDate day) {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("validFrom"), day),
                cb.greaterThanOrEqualTo(root.get("validUntil"), day));
    }

    /** Still valid on or after that day (the last valid day is not before it). */
    public static Specification<Coupon> validUntilOnOrAfter(LocalDate day) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("validUntil"), day);
    }

    /** Valid from that day or earlier (the first valid day is not after it). */
    public static Specification<Coupon> validFromOnOrBefore(LocalDate day) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("validFrom"), day);
    }
}
