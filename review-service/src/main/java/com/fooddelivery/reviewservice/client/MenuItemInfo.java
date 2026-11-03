package com.fooddelivery.reviewservice.client;

/** Current data of a menu item from catalog-service. Fields catalog-service does not send stay null. */
public record MenuItemInfo(Long id, String name, String imageUrl, String categoryName) {
}
