package com.fooddelivery.restaurantservice.service;

import com.fooddelivery.restaurantservice.client.UserServiceClient;
import com.fooddelivery.restaurantservice.dto.response.AdminRestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.OwnerResponse;
import com.fooddelivery.restaurantservice.dto.response.OwnerResponse.LookupStatus;
import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.exception.RemoteServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Admin review screen: restaurant data (this service) + owner data (user-service).
 * <p>
 * Deliberately NOT @Transactional: the database read finishes before the remote call starts,
 * so no database connection is held while waiting for another service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRestaurantDetailsService {

    private final AdminRestaurantService adminRestaurantService;
    private final UserServiceClient userServiceClient;

    public AdminRestaurantDetailsResponse getRestaurantDetails(Long restaurantId) {
        RestaurantDetailsResponse restaurant = adminRestaurantService.getRestaurant(restaurantId);
        return new AdminRestaurantDetailsResponse(restaurant, lookupOwner(restaurant.ownerUserId()));
    }

    /** Fallback: if user-service is down, the admin still sees the restaurant, with the owner marked UNAVAILABLE. */
    private OwnerResponse lookupOwner(Long ownerUserId) {
        try {
            return userServiceClient.findUser(ownerUserId)
                    .map(user -> new OwnerResponse(user.id(), LookupStatus.FOUND, user.fullName(), user.email(),
                            user.phone(), user.status()))
                    .orElseGet(() -> new OwnerResponse(ownerUserId, LookupStatus.NOT_FOUND, null, null, null, null));
        } catch (RemoteServiceException e) {
            log.warn("Owner lookup failed, returning restaurant without owner details. ownerUserId={} reason={}",
                    ownerUserId, e.getMessage());
            return new OwnerResponse(ownerUserId, LookupStatus.UNAVAILABLE, null, null, null, null);
        }
    }
}
