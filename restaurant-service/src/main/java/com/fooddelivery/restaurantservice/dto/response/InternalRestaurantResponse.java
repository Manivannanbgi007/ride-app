package com.fooddelivery.restaurantservice.dto.response;

import com.fooddelivery.restaurantservice.entity.RestaurantStatus;

/**
 * Basic restaurant data for other microservices: ownership and status checks, whether the restaurant
 * accepts orders right now, and where delivery partners pick orders up.
 * {@code version} lets services that keep a copy (fed by RestaurantChanged events) tell
 * whether this data is newer than their copy.
 */
public record InternalRestaurantResponse(
        Long id,
        Long ownerUserId,
        String name,
        RestaurantStatus status,
        boolean openNow,
        Long version,
        AddressResponse address
) {
}
