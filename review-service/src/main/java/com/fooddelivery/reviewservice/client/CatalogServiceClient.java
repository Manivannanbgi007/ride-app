package com.fooddelivery.reviewservice.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The internal API of catalog-service (the API Gateway does not route /api/v1/internal/**). */
@Slf4j
@Component
public class CatalogServiceClient implements MenuItemLookup {

    /** catalog-service accepts at most this many ids per call. */
    static final int MAX_IDS = 100;

    private final RestClient restClient;

    public CatalogServiceClient(@Qualifier("catalogServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Map<Long, MenuItemInfo> findMenuItems(Collection<Long> ids) {
        Map<Long, MenuItemInfo> found = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return found;
        }
        String idList = String.join(",", ids.stream().limit(MAX_IDS).map(String::valueOf).toList());
        try {
            List<MenuItemInfo> items = restClient.get()
                    .uri(uri -> uri.path("/api/v1/internal/menu-items").queryParam("ids", idList).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MenuItemInfo>>() {
                    });
            if (items != null) {
                items.forEach(item -> found.put(item.id(), item));
            }
        } catch (RestClientException e) {
            // Decoration only: the featured dishes are still shown, with the names saved in the reviews.
            log.warn("catalog-service is unavailable, showing dishes without pictures: {}", e.getMessage());
        }
        return found;
    }
}
