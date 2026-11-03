package com.fooddelivery.chatservice.client;

import java.util.Optional;

import com.fooddelivery.chatservice.exception.DependencyUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The internal API of user-service (the API Gateway does not route /api/v1/internal/**). */
@Component
public class UserServiceClient implements UserLookup {

    private final RestClient restClient;

    public UserServiceClient(@Qualifier("userServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<UserInfo> findUser(long userId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/v1/internal/users/{userId}", userId)
                    .retrieve()
                    .body(UserInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            // 404 is an answer ("no such user"), not a failure
            return Optional.empty();
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("user-service", e);
        }
    }
}
