package com.fooddelivery.orderservice.client;

import java.util.Optional;

import com.fooddelivery.orderservice.client.RemoteRecords.RestaurantSummary;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls restaurant-service: owner, status and whether the restaurant is open right now. */
@Component
public class RestaurantServiceClient {

    private static final String SERVICE = "restaurant-service";

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public RestaurantServiceClient(@Qualifier("restaurantServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    public Optional<RestaurantSummary> findRestaurant(Long restaurantId) {
        return remoteCalls.call(SERVICE, () -> {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/api/v1/internal/restaurants/{restaurantId}", restaurantId)
                        .retrieve()
                        .body(RestaurantSummary.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }
}
