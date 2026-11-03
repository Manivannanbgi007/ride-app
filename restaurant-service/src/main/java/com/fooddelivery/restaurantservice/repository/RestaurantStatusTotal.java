package com.fooddelivery.restaurantservice.repository;

import com.fooddelivery.restaurantservice.entity.RestaurantStatus;

/** Number of restaurants in one status, counted in the database. */
public interface RestaurantStatusTotal {

    RestaurantStatus getStatus();

    long getTotal();
}
