package com.fooddelivery.catalogservice.repository;

/** Projection: menu items of a category, all and orderable (statistics). */
public interface CategoryItemStats {

    Long getCategoryId();

    String getCategoryName();

    long getTotalItems();

    long getOrderableItems();
}
