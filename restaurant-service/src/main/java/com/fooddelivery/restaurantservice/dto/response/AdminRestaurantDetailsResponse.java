package com.fooddelivery.restaurantservice.dto.response;

/** Admin review screen: the restaurant plus its owner from user-service. */
public record AdminRestaurantDetailsResponse(
        RestaurantDetailsResponse restaurant,
        OwnerResponse owner
) {
}
