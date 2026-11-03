package com.fooddelivery.aimcpserver.action;

import com.fooddelivery.aimcpserver.security.Role;

/**
 * Every change the assistant can prepare, with the one role allowed to ask for it and the business API that makes it.
 * The path is fixed here and only numeric ids are put into it: nothing the model writes can choose what is called.
 * Each of these is safe to repeat: the business service refuses a second accept, cancel or pickup (409), so a retry
 * after a timeout can never make the change twice.
 */
public enum ActionKind {

    CANCEL_MY_ORDER(Role.CUSTOMER, "/api/v1/orders/{id}/cancel"),

    ACCEPT_ORDER(Role.RESTAURANT_ADMIN, "/api/v1/restaurants/mine/{restaurantId}/orders/{id}/accept"),
    REJECT_ORDER(Role.RESTAURANT_ADMIN, "/api/v1/restaurants/mine/{restaurantId}/orders/{id}/reject"),
    START_PREPARING(Role.RESTAURANT_ADMIN, "/api/v1/restaurants/mine/{restaurantId}/orders/{id}/preparing"),
    MARK_READY(Role.RESTAURANT_ADMIN, "/api/v1/restaurants/mine/{restaurantId}/orders/{id}/ready"),
    SET_ITEM_AVAILABILITY(Role.RESTAURANT_ADMIN, "/api/v1/restaurants/mine/{restaurantId}/menu-items/{id}/availability"),

    ACCEPT_DELIVERY(Role.DELIVERY_PARTNER, "/api/v1/delivery/deliveries/mine/{id}/accept"),
    DECLINE_DELIVERY(Role.DELIVERY_PARTNER, "/api/v1/delivery/deliveries/mine/{id}/decline"),
    MARK_PICKED_UP(Role.DELIVERY_PARTNER, "/api/v1/delivery/deliveries/mine/{id}/pickup"),
    MARK_DELIVERED(Role.DELIVERY_PARTNER, "/api/v1/delivery/deliveries/mine/{id}/deliver"),

    ADMIN_CANCEL_ORDER(Role.PLATFORM_ADMIN, "/api/v1/admin/orders/{id}/cancel"),
    REASSIGN_DELIVERY(Role.PLATFORM_ADMIN, "/api/v1/admin/deliveries/{id}/reassign");

    private static final String RESTAURANT = "{restaurantId}";

    private final Role role;
    private final String pathTemplate;

    ActionKind(Role role, String pathTemplate) {
        this.role = role;
        this.pathTemplate = pathTemplate;
    }

    public Role role() {
        return role;
    }

    /** True for the owner's changes, which are made in one of the owner's restaurants. */
    public boolean needsRestaurant() {
        return pathTemplate.contains(RESTAURANT);
    }

    /** The business API path for these ids (restaurantId only for the owner's changes). */
    public String path(Long restaurantId, long targetId) {
        if (targetId <= 0 || needsRestaurant() && (restaurantId == null || restaurantId <= 0)) {
            throw new IllegalArgumentException("Missing id for " + name());
        }
        String path = pathTemplate.replace("{id}", Long.toString(targetId));
        return needsRestaurant() ? path.replace(RESTAURANT, Long.toString(restaurantId)) : path;
    }
}
