package com.fooddelivery.notificationservice.repository;

import java.time.Instant;

import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.entity.NotificationType;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks of the admin notification search. */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> hasRecipient(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("recipientUserId"), userId);
    }

    public static Specification<Notification> forOrder(Long orderId) {
        return (root, query, cb) -> cb.equal(root.get("orderId"), orderId);
    }

    public static Specification<Notification> forRestaurant(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
    }

    public static Specification<Notification> hasType(NotificationType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Notification> hasChannel(NotificationChannel channel) {
        return (root, query, cb) -> cb.equal(root.get("channel"), channel);
    }

    public static Specification<Notification> hasStatus(NotificationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Notification> createdFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Notification> createdBefore(Instant before) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), before);
    }
}
