package com.fooddelivery.paymentservice.repository;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable query conditions for the admin payment and refund lists.
 * Generic over the entity, because payments and refunds share field names (id, orderId, status, ...).
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static <T> Specification<T> fieldEquals(String field, Object value) {
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    /** At or after this moment (inclusive). */
    public static <T> Specification<T> atOrAfter(String field, Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), from);
    }

    /** Before this moment (exclusive). */
    public static <T> Specification<T> before(String field, Instant before) {
        return (root, query, cb) -> cb.lessThan(root.get(field), before);
    }
}
