package com.fooddelivery.promotionservice.entity;

/** One use of a coupon, from checkout to delivery. */
public enum RedemptionStatus {
    /** Taken at checkout; the order is being created. */
    RESERVED,
    /** The order exists (OrderCreated). */
    APPLIED,
    /** The order was delivered: the use is final. */
    REDEEMED,
    /** The order was cancelled or rejected: the use was given back. */
    RELEASED,
    /** No order was ever placed with it (checkout failed half way): the use was given back. */
    EXPIRED;

    /** Counts against the usage limits. */
    public boolean isUse() {
        return this == RESERVED || this == APPLIED || this == REDEEMED;
    }
}
