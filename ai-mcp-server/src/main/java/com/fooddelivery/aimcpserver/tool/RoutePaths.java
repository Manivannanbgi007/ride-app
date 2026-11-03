package com.fooddelivery.aimcpserver.tool;

import com.fooddelivery.aimcpserver.dto.Link;

/**
 * Front-end routes (front-app src/utils/constants.ts: ROUTES, CUSTOMER_ROUTES, OWNER_ROUTES, PARTNER_ROUTES), built
 * from numeric ids only. Customer links use the short paths (/orders/..., /restaurants/...) that the front-end
 * forwards into /customer.
 */
public final class RoutePaths {

    private RoutePaths() {
    }

    public static Link adminOrder(long orderId) {
        return new Link("Open order", "/admin/orders/" + orderId);
    }

    public static Link adminRestaurant(long restaurantId) {
        return new Link("Open restaurant", "/admin/restaurants/" + restaurantId);
    }

    public static Link adminDelivery(long deliveryId) {
        return new Link("Open delivery", "/admin/deliveries/" + deliveryId);
    }

    public static Link adminDeliveries() {
        return new Link("All deliveries", "/admin/deliveries");
    }

    public static Link adminOrders() {
        return new Link("All orders", "/admin/orders");
    }

    public static Link adminRestaurants() {
        return new Link("All restaurants", "/admin/restaurants");
    }

    public static Link restaurantPage(long restaurantId) {
        return new Link("View restaurant", "/restaurants/" + restaurantId);
    }

    public static Link customerOrder(long orderId) {
        return new Link("View order", "/orders/" + orderId);
    }

    public static Link offers() {
        return new Link("See all offers", "/offers");
    }

    /** The owner's page of one order of their restaurant (OWNER_ROUTES.orderDetails). */
    public static Link ownerOrder(long orderId) {
        return new Link("Open order", "/owner/orders/" + orderId);
    }

    public static Link ownerMenu() {
        return new Link("Open menu", "/owner/menu");
    }

    /** The delivery partner's page of one of their deliveries (PARTNER_ROUTES.deliveryDetails). */
    public static Link partnerDelivery(long deliveryId) {
        return new Link("Open delivery", "/partner/deliveries/" + deliveryId);
    }
}
