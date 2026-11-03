package com.fooddelivery.orderservice.repository;

import java.time.Instant;

import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

/** Reusable query conditions for order lists; services combine only the requested filters. */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasId(Long orderId) {
        return (root, query, cb) -> cb.equal(root.get("id"), orderId);
    }

    public static Specification<Order> ofCustomer(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Order> ofRestaurant(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> statusIsNot(OrderStatus status) {
        return (root, query, cb) -> cb.notEqual(root.get("status"), status);
    }

    public static Specification<Order> hasPaymentStatus(PaymentStatus paymentStatus) {
        return (root, query, cb) -> cb.equal(root.get("paymentStatus"), paymentStatus);
    }

    /** Created at or after this moment (inclusive). */
    public static Specification<Order> createdFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    /** Created before this moment (exclusive). */
    public static Specification<Order> createdBefore(Instant before) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), before);
    }
}
