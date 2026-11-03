package com.fooddelivery.catalogservice.repository;

import com.fooddelivery.catalogservice.entity.MenuItemStatus;

/** Number of menu items per status and availability, counted in the database. */
public interface MenuItemTotals {

    MenuItemStatus getStatus();

    boolean isAvailable();

    long getTotal();
}
