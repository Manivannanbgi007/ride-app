package com.fooddelivery.orderservice.client;

import java.math.BigDecimal;

/**
 * Data received from other services' internal APIs.
 * Status-like fields are plain strings ("tolerant reader"): new values there must not break this service.
 */
public final class RemoteRecords {

    /** Status of an active user, restaurant or menu item in the other services. */
    static final String ACTIVE = "ACTIVE";

    private RemoteRecords() {
    }

    /** From user-service. */
    public record UserSummary(Long id, String fullName, String email, String phone, String role, String status) {

        public boolean isActive() {
            return ACTIVE.equals(status);
        }
    }

    /** From user-service. */
    public record AddressSummary(Long id, Long userId, String line1, String line2, String city, String state,
                                 String postalCode, String country, BigDecimal latitude, BigDecimal longitude) {
    }

    /** From restaurant-service. */
    public record RestaurantSummary(Long id, Long ownerUserId, String name, String status, boolean openNow) {

        public boolean isActive() {
            return ACTIVE.equals(status);
        }
    }

    /**
     * From promotion-service: would the coupon work for this cart.
     *
     * @param reason why not, in words a customer can read (null when applicable)
     */
    public record CouponCheck(String code, boolean applicable, BigDecimal discountAmount, String reason,
                              String description) {
    }

    /** From promotion-service: the coupon use reserved for a checkout. */
    public record CouponReservation(Long reservationId, String code, BigDecimal discountAmount, String status) {
    }

    /** From catalog-service: the parts of a menu item that only decorate an admin screen. */
    public record MenuItemDetails(Long id, String name, String imageUrl, String categoryName) {
    }

    /** From catalog-service. */
    public record MenuItemSummary(Long id, Long restaurantId, String name, BigDecimal price, String foodType,
                                  String status, boolean available) {

        /** Listed on the menu and in stock. */
        public boolean isOrderable() {
            return ACTIVE.equals(status) && available;
        }
    }

    /** From catalog-service: a menu item as a customer's dish card shows it (price, stock, picture, category). */
    public record MenuItemCard(Long id, Long restaurantId, String name, BigDecimal price, String foodType,
                               String status, boolean available, String imageUrl, String categoryName) {

        /** Listed on the menu and in stock. */
        public boolean isOrderable() {
            return ACTIVE.equals(status) && available;
        }
    }
}
