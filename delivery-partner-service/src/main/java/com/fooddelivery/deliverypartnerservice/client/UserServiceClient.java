package com.fooddelivery.deliverypartnerservice.client;

import java.util.Optional;

import com.fooddelivery.deliverypartnerservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks user-service about a partner whose UserRegistered event has not arrived yet (for example an account
 * created before this service first started). All remote-call details stay in this class.
 */
@Component
public class UserServiceClient {

    public static final String DELIVERY_PARTNER = "DELIVERY_PARTNER";

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public UserServiceClient(@Qualifier("userServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** A user as user-service reports it. Role and status are plain strings ("tolerant reader"). */
    public record UserSummary(Long id, String fullName, String email, String phone, String role, String status) {

        public boolean isDeliveryPartner() {
            return DELIVERY_PARTNER.equals(role);
        }
    }

    /**
     * @return the user, or empty if user-service says it does not exist
     * @throws RemoteServiceException if user-service cannot be reached or fails
     */
    public Optional<UserSummary> findUser(Long userId) {
        return remoteCalls.call("user-service", () -> {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/api/v1/internal/users/{userId}", userId)
                        .retrieve()
                        .body(UserSummary.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException("user-service", e);
            }
        });
    }
}
