package com.fooddelivery.reviewservice.client;

import java.util.Optional;

import com.fooddelivery.reviewservice.exception.DependencyUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The internal API of order-service (the API Gateway does not route /api/v1/internal/**). */
@Component
public class OrderServiceClient implements OrderLookup {

    private final RestClient restClient;

    public OrderServiceClient(@Qualifier("orderServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<OrderReviewInfo> findOrder(long orderId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/v1/internal/orders/{orderId}/review-info", orderId)
                    .retrieve()
                    .body(OrderReviewInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            // 404 is an answer ("no such order"), not a failure
            return Optional.empty();
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("order-service", e);
        }
    }
}
