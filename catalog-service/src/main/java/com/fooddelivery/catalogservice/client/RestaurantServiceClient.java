package com.fooddelivery.catalogservice.client;

import java.util.Optional;

import com.fooddelivery.catalogservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls restaurant-service. All remote-call details stay in this class.
 * Used only for restaurants not yet in the local copy (see RestaurantDirectory).
 * (Retries and a circuit breaker are added with Resilience4j in a later phase.)
 */
@Component
public class RestaurantServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public RestaurantServiceClient(@Qualifier("restaurantServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /**
     * @return the restaurant, or empty if restaurant-service says it does not exist
     * @throws RemoteServiceException if restaurant-service cannot be reached or fails
     */
    public Optional<RestaurantSummary> findRestaurant(Long restaurantId) {
        return remoteCalls.call("restaurant-service", () -> {
            try {
                RestaurantSummary restaurant = restClient.get()
                        .uri("/api/v1/internal/restaurants/{restaurantId}", restaurantId)
                        .retrieve()
                        .body(RestaurantSummary.class);
                return Optional.ofNullable(restaurant);
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException("restaurant-service", e);
            }
        });
    }
}
