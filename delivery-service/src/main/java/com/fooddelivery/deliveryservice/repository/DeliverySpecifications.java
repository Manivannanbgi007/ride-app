package com.fooddelivery.deliveryservice.repository;

import java.time.Instant;
import java.util.Collection;

import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks of the admin delivery search (partners are searched in delivery-partner-service). */
public final class DeliverySpecifications {

    private static final String STATUS = "status";

    private DeliverySpecifications() {
    }

    public static Specification<Delivery> hasStatus(DeliveryStatus status) {
        return (root, query, cb) -> cb.equal(root.get(STATUS), status);
    }

    public static Specification<Delivery> hasStatusIn(Collection<DeliveryStatus> statuses) {
        return (root, query, cb) -> root.get(STATUS).in(statuses);
    }

    public static Specification<Delivery> forOrder(Long orderId) {
        return (root, query, cb) -> cb.equal(root.get("orderId"), orderId);
    }

    public static Specification<Delivery> forPartner(Long partnerUserId) {
        return (root, query, cb) -> cb.equal(root.get("partnerUserId"), partnerUserId);
    }

    public static Specification<Delivery> forCustomer(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Delivery> forRestaurant(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
    }

    public static Specification<Delivery> inPickupCity(String city) {
        return (root, query, cb) -> cb.equal(root.get("pickupAddress").get("city"), city);
    }

    public static Specification<Delivery> createdFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Delivery> createdBefore(Instant before) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), before);
    }

    public static Specification<Delivery> readyBefore(Instant before) {
        return (root, query, cb) -> cb.lessThan(root.get("readyAt"), before);
    }
}
