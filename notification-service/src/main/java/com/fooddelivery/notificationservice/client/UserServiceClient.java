package com.fooddelivery.notificationservice.client;

import java.util.Optional;

import com.fooddelivery.notificationservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks user-service for a recipient's contact details when a notification is sent, so a changed
 * email address or phone number is always used. All remote-call details stay in this class.
 */
@Component
public class UserServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public UserServiceClient(@Qualifier("userServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** Contact details as user-service reports them. Role and status are plain strings ("tolerant reader"). */
    public record UserContact(Long id, String fullName, String email, String phone, String role, String status) {

        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }

    /**
     * @return the user, or empty if user-service says it does not exist
     * @throws RemoteServiceException if user-service cannot be reached or fails (the notification is retried)
     */
    public Optional<UserContact> findUser(Long userId) {
        return remoteCalls.call("user-service", () -> {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/api/v1/internal/users/{userId}", userId)
                        .retrieve()
                        .body(UserContact.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException("user-service", e);
            }
        });
    }
}
