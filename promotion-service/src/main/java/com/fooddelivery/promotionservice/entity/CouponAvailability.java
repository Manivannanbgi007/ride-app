package com.fooddelivery.promotionservice.entity;

/** Whether a coupon can be used today, worked out from its status, dates and uses (not stored). */
public enum CouponAvailability {
    AVAILABLE,
    /** Active, but its first day has not come yet. */
    SCHEDULED,
    EXPIRED,
    /** The usage limit is reached. */
    USED_UP,
    INACTIVE
}
