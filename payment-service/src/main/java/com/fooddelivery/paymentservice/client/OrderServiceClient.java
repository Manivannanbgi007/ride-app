package com.fooddelivery.paymentservice.client;

import java.math.BigDecimal;
import java.util.Optional;

import com.fooddelivery.paymentservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls order-service to read an order (payment results go to order-service as Kafka events). */
@Component
public class OrderServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public OrderServiceClient(@Qualifier("orderServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** Order data as order-service reports it. Status fields are plain strings ("tolerant reader"). */
    public record OrderSummary(Long id, Long customerId, Long restaurantId, String status, String paymentMethod,
                               String paymentStatus, BigDecimal totalAmount, String currency) {
    }

    public Optional<OrderSummary> findOrder(Long orderId) {
        return remoteCalls.call("order-service", () -> {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/api/v1/internal/orders/{orderId}", orderId)
                        .retrieve()
                        .body(OrderSummary.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException("order-service", e);
            }
        });
    }
}
