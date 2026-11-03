package com.fooddelivery.restaurantservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import com.fooddelivery.restaurantservice.exception.RemoteServiceException;
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

/** The HTTP client against a mocked user-service: success, "not found" and failure. */
class UserServiceClientTest {

    private MockRestServiceServer userService;
    private UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://user-service");
        userService = MockRestServiceServer.bindTo(builder).build();
        userServiceClient = new UserServiceClient(builder.build(), new RemoteCalls(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().retryOnException(new ConnectionFailure()).build()),
                BulkheadRegistry.ofDefaults()));
    }

    @Test
    void returnsUserWhenFound() {
        userService.expect(requestTo("http://user-service/api/v1/internal/users/5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": 5, "fullName": "Arjun Mehta", "email": "arjun@example.com",
                         "phone": "+919800000000", "role": "RESTAURANT_ADMIN", "status": "ACTIVE",
                         "someNewField": "ignored"}
                        """, MediaType.APPLICATION_JSON));

        Optional<UserSummary> user = userServiceClient.findUser(5L);

        assertThat(user).hasValueSatisfying(found -> {
            assertThat(found.fullName()).isEqualTo("Arjun Mehta");
            assertThat(found.role()).isEqualTo("RESTAURANT_ADMIN");
        });
        userService.verify();
    }

    @Test
    void returnsEmptyWhenUserDoesNotExist() {
        userService.expect(requestTo("http://user-service/api/v1/internal/users/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(userServiceClient.findUser(99L)).isEmpty();
    }

    @Test
    void failureOfUserServiceBecomesRemoteServiceException() {
        userService.expect(requestTo("http://user-service/api/v1/internal/users/5"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> userServiceClient.findUser(5L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("user-service is unavailable");
    }
}
