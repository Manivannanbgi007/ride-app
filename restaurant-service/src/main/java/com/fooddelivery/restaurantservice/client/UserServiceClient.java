package com.fooddelivery.restaurantservice.client;

import java.util.Optional;

import com.fooddelivery.restaurantservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls user-service. All remote-call details (URL, errors) stay in this class,
 * so services just ask "who is user 42?" and tests can replace it easily.
 * (Retries and a circuit breaker are added with Resilience4j in a later phase.)
 */
@Component
public class UserServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public UserServiceClient(@Qualifier("userServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /**
     * @return the user, or empty if user-service says the user does not exist
     * @throws RemoteServiceException if user-service cannot be reached or fails
     */
    public Optional<UserSummary> findUser(Long userId) {
        return remoteCalls.call("user-service", () -> {
            try {
                UserSummary user = restClient.get()
                        .uri("/api/v1/internal/users/{userId}", userId)
                        .retrieve()
                        .body(UserSummary.class);
                return Optional.ofNullable(user);
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException("user-service", e);
            }
        });
    }
}
