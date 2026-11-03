package com.fooddelivery.catalogservice.service;

import com.fooddelivery.catalogservice.client.RestaurantSummary;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.exception.RestaurantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks whether a restaurant may be used, with data from {@link RestaurantDirectory}
 * (the local copy, or restaurant-service for restaurants not known locally yet).
 * Restaurants that do not exist, or belong to someone else, are reported as "not found".
 * If a restaurant is unknown locally and restaurant-service is down, RemoteServiceException
 * becomes 503: we never guess about ownership.
 */
@Component
@RequiredArgsConstructor
public class RestaurantAccessGuard {

    private final RestaurantDirectory restaurantDirectory;

    /** Owner reading their menu: the restaurant must belong to the caller. */
    public RestaurantSummary requireOwnedRestaurant(Long restaurantId, Long ownerUserId) {
        return restaurantDirectory.findRestaurant(restaurantId)
                .filter(restaurant -> ownerUserId.equals(restaurant.ownerUserId()))
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }

    /** Owner changing their menu: as above, and the restaurant must not be suspended. */
    public RestaurantSummary requireEditableOwnedRestaurant(Long restaurantId, Long ownerUserId) {
        return requireNotSuspended(requireOwnedRestaurant(restaurantId, ownerUserId));
    }

    /** Platform admin changing any menu: the restaurant must exist and must not be suspended. */
    public RestaurantSummary requireEditableRestaurant(Long restaurantId) {
        return requireNotSuspended(restaurantDirectory.findRestaurant(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId)));
    }

    private RestaurantSummary requireNotSuspended(RestaurantSummary restaurant) {
        if (restaurant.isSuspended()) {
            throw new BusinessRuleException("The menu of a suspended restaurant cannot be changed. Contact platform support.");
        }
        return restaurant;
    }

    /** Customers only see menus of ACTIVE restaurants. */
    public RestaurantSummary requireActiveRestaurant(Long restaurantId) {
        return restaurantDirectory.findRestaurant(restaurantId)
                .filter(RestaurantSummary::isActive)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }
}
