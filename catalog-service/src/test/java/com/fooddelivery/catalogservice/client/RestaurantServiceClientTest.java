package com.fooddelivery.catalogservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import com.fooddelivery.catalogservice.exception.RemoteServiceException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The HTTP client against a mocked restaurant-service: success, "not found" and failure. */
class RestaurantServiceClientTest {

    private MockRestServiceServer restaurantService;
    private RestaurantServiceClient restaurantServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://restaurant-service");
        restaurantService = MockRestServiceServer.bindTo(builder).build();
        restaurantServiceClient = new RestaurantServiceClient(builder.build(),
                new RemoteCalls(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().retryOnException(new ConnectionFailure()).build()),
                BulkheadRegistry.ofDefaults()));
    }

    @Test
    void returnsRestaurantWhenFound() {
        restaurantService.expect(requestTo("http://restaurant-service/api/v1/internal/restaurants/5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": 5, "ownerUserId": 42, "name": "Spice Hub", "status": "ACTIVE",
                         "someNewField": "ignored"}
                        """, MediaType.APPLICATION_JSON));

        Optional<RestaurantSummary> restaurant = restaurantServiceClient.findRestaurant(5L);

        assertThat(restaurant).hasValueSatisfying(found -> {
            assertThat(found.ownerUserId()).isEqualTo(42L);
            assertThat(found.isActive()).isTrue();
        });
        restaurantService.verify();
    }

    @Test
    void returnsEmptyWhenRestaurantDoesNotExist() {
        restaurantService.expect(requestTo("http://restaurant-service/api/v1/internal/restaurants/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(restaurantServiceClient.findRestaurant(99L)).isEmpty();
    }

    @Test
    void failureOfRestaurantServiceBecomesRemoteServiceException() {
        restaurantService.expect(requestTo("http://restaurant-service/api/v1/internal/restaurants/5"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> restaurantServiceClient.findRestaurant(5L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("restaurant-service is unavailable");
    }
}
