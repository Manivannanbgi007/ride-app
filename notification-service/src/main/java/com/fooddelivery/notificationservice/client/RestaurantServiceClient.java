package com.fooddelivery.notificationservice.client;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fooddelivery.notificationservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Asks restaurant-service who owns a restaurant, for notifications addressed to a restaurant. */
@Component
public class RestaurantServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public RestaurantServiceClient(@Qualifier("restaurantServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    public record RestaurantSummary(Long id, Long ownerUserId, String name, String status) {
    }

    /** One of an owner's restaurants, as restaurant-service lists them (only the id is used). */
    public record OwnedRestaurant(Long id) {
    }

    /**
     * The restaurants a restaurant owner owns (all statuses), for the live updates: an owner is sent the events of
     * these restaurants only. Asked through restaurant-service's internal API (with the services' shared token).
     *
     * @throws RemoteServiceException if restaurant-service cannot be reached or fails
     */
    public Set<Long> findOwnedRestaurantIds(Long ownerUserId) {
        return remoteCalls.call("restaurant-service", () -> {
            try {
                OwnedRestaurant[] restaurants = restClient.get()
                        .uri("/api/v1/internal/restaurants?ownerUserId={ownerUserId}", ownerUserId)
                        .retrieve()
                        .body(OwnedRestaurant[].class);
                return restaurants == null ? Set.<Long>of()
                        : Arrays.stream(restaurants).map(OwnedRestaurant::id).collect(Collectors.toUnmodifiableSet());
            } catch (RestClientException e) {
                throw new RemoteServiceException("restaurant-service", e);
            }
        });
    }

    /**
     * @return the restaurant, or empty if restaurant-service says it does not exist
     * @throws RemoteServiceException if restaurant-service cannot be reached or fails (the notification is retried)
     */
    public Optional<RestaurantSummary> findRestaurant(Long restaurantId) {
        return remoteCalls.call("restaurant-service", () -> {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/api/v1/internal/restaurants/{restaurantId}", restaurantId)
                        .retrieve()
                        .body(RestaurantSummary.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException("restaurant-service", e);
            }
        });
    }
}
