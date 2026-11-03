package com.fooddelivery.orderservice.client;

import java.util.Optional;

import com.fooddelivery.orderservice.client.RemoteRecords.AddressSummary;
import com.fooddelivery.orderservice.client.RemoteRecords.UserSummary;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls user-service: the customer placing the order and their delivery address. */
@Component
public class UserServiceClient {

    private static final String SERVICE = "user-service";

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public UserServiceClient(@Qualifier("userServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    public Optional<UserSummary> findUser(Long userId) {
        return get("/api/v1/internal/users/{userId}", UserSummary.class, userId);
    }

    /** Empty when the address does not exist or belongs to another user. */
    public Optional<AddressSummary> findAddress(Long userId, Long addressId) {
        return get("/api/v1/internal/users/{userId}/addresses/{addressId}", AddressSummary.class, userId, addressId);
    }

    private <T> Optional<T> get(String uri, Class<T> type, Object... uriVariables) {
        return remoteCalls.call(SERVICE, () -> {
            try {
                return Optional.ofNullable(restClient.get().uri(uri, uriVariables).retrieve().body(type));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }
}
