package com.fooddelivery.catalogservice.exception;

public class RestaurantNotFoundException extends ResourceNotFoundException {

    public RestaurantNotFoundException(Long restaurantId) {
        super("Restaurant not found: " + restaurantId);
    }
}
