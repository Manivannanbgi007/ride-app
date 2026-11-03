package com.fooddelivery.deliveryservice.client;

import java.math.BigDecimal;
import java.util.Optional;

import com.fooddelivery.deliveryservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Asks order-service for the delivery details of an order whose OrderCreated event has not arrived. */
@Component
public class OrderServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public OrderServiceClient(@Qualifier("orderServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** The parts of order-service's internal order response this service reads. */
    public record OrderSummary(Long id, Long customerId, Long restaurantId, String status, String paymentMethod,
                               BigDecimal totalAmount, String currency, String restaurantName, String customerName,
                               String customerPhone, RemoteRecords.RemoteAddress deliveryAddress) {
    }

    /**
     * @return the order, or empty if order-service says it does not exist
     * @throws RemoteServiceException if order-service cannot be reached or fails (the job tries again later)
     */
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
