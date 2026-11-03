package com.fooddelivery.chatservice.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The internal presence API of notification-service, which owns the WebSocket connections. */
@Slf4j
@Component
public class NotificationServiceClient implements PresenceLookup {

    private final RestClient restClient;

    public NotificationServiceClient(@Qualifier("notificationServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** The answer of notification-service: {"online": [1, 3]}. */
    record PresenceResponse(List<Long> online) {
    }

    @Override
    public Optional<Set<Long>> findOnline(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Optional.of(Set.of());
        }
        String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            PresenceResponse response = restClient.get()
                    .uri("/api/v1/internal/presence?userIds={ids}", ids)
                    .retrieve()
                    .body(PresenceResponse.class);
            return response == null || response.online() == null
                    ? Optional.empty()
                    : Optional.of(new HashSet<>(response.online()));
        } catch (RestClientException e) {
            log.debug("Presence unknown: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
