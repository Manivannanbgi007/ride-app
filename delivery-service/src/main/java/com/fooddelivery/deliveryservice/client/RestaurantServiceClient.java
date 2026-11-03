package com.fooddelivery.deliveryservice.client;

import java.util.Optional;

import com.fooddelivery.deliveryservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks restaurant-service for a pickup address this service has no copy of (no RestaurantChanged event yet), and
 * for the owner of a restaurant (the owner endpoints check it; the pickup point copy does not keep the owner).
 */
@Component
public class RestaurantServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public RestaurantServiceClient(@Qualifier("restaurantServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** The parts of restaurant-service's internal restaurant response this service reads. */
    public record RestaurantSummary(Long id, Long ownerUserId, String name, Long version,
                                    RemoteRecords.RemoteAddress address) {
    }

    /**
     * @return the restaurant, or empty if restaurant-service says it does not exist
     * @throws RemoteServiceException if restaurant-service cannot be reached or fails (the job tries again later)
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
