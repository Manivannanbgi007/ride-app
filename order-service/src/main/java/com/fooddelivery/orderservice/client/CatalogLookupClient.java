package com.fooddelivery.orderservice.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemDetails;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Decorative catalog data (image, category) for admin screens. Unlike {@link CatalogServiceClient}, which checkout
 * relies on and which fails loudly, this is best effort: when catalog-service is down the answer is just empty.
 */
@Slf4j
@Component
public class CatalogLookupClient {

    private static final ParameterizedTypeReference<List<MenuItemDetails>> DETAILS_LIST = new ParameterizedTypeReference<>() {
    };

    private static final String SERVICE = "catalog-service";

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public CatalogLookupClient(@Qualifier("catalogServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** Unknown ids are missing from the result; any failure gives an empty result. */
    public Map<Long, MenuItemDetails> findDetails(Collection<Long> menuItemIds) {
        if (menuItemIds.isEmpty()) {
            return Map.of();
        }
        try {
            return remoteCalls.call(SERVICE, () -> {
                try {
                    List<MenuItemDetails> items = restClient.get()
                            .uri(uriBuilder -> uriBuilder.path("/api/v1/internal/menu-items")
                                    .queryParam("ids", menuItemIds.toArray())
                                    .build())
                            .retrieve()
                            .body(DETAILS_LIST);
                    return items == null ? Map.<Long, MenuItemDetails>of()
                            : items.stream().collect(Collectors.toMap(MenuItemDetails::id, Function.identity(), (a, b) -> a));
                } catch (RestClientException e) {
                    throw new RemoteServiceException(SERVICE, e);
                }
            });
        } catch (RemoteServiceException e) {
            log.warn("Catalog details unavailable, continuing without them: {}", e.getMessage());
            return Map.of();
        }
    }
}
