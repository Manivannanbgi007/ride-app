package com.fooddelivery.searchservice.client;

import com.fooddelivery.searchservice.document.SearchedIndex;
import com.fooddelivery.searchservice.dto.response.SourceVersions;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import com.fooddelivery.searchservice.web.CallerInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks the services that own the data (MySQL) what exists and at which version, and to publish it again. Elasticsearch
 * never decides: when the two differ, this is what is right.
 */
@Component
public class SourceOfTruthClient {

    private final RestClient restaurantService;
    private final RestClient catalogService;

    public SourceOfTruthClient(@Qualifier("restaurantServiceRestClient") RestClient restaurantService,
                               @Qualifier("catalogServiceRestClient") RestClient catalogService) {
        this.restaurantService = restaurantService;
        this.catalogService = catalogService;
    }

    private record Republished(int republished) {
    }

    /** Up to size items with an id above afterId, in id order. */
    public SourceVersions versions(SearchedIndex index, long afterId, int size) {
        String path = switch (index) {
            case RESTAURANT -> "/api/v1/internal/restaurants/versions";
            case FOOD -> "/api/v1/internal/search-feed/menu-items";
            case CATEGORY -> "/api/v1/internal/search-feed/categories";
        };
        try {
            SourceVersions page = clientOf(index).get()
                    .uri(uri -> uri.path(path).queryParam("afterId", afterId).queryParam("size", size).build())
                    .retrieve()
                    .body(SourceVersions.class);
            if (page == null) {
                throw new SourceUnavailableException(serviceOf(index), new IllegalStateException("empty answer"));
            }
            return page;
        } catch (RestClientException e) {
            throw new SourceUnavailableException(serviceOf(index), e);
        }
    }

    /** Whether the owning service still has this item (a fresh answer for one id). */
    public boolean exists(SearchedIndex index, long id) {
        SourceVersions page = versions(index, id - 1, 1);
        return !page.entries().isEmpty() && page.entries().getFirst().id() == id;
    }

    /**
     * Asks the owning service to publish the current state of everything again (its own audited admin action, as the
     * admin who asked). Returns how many events it published.
     */
    public int republish(SearchedIndex index, CallerInfo admin) {
        String path = switch (index) {
            case RESTAURANT -> "/api/v1/admin/restaurants/events/republish";
            case FOOD -> "/api/v1/admin/menu-items/events/republish";
            case CATEGORY -> "/api/v1/admin/categories/events/republish";
        };
        try {
            Republished result = clientOf(index).post()
                    .uri(path)
                    // As the admin who asked: the owning service checks their token and role itself
                    .headers(headers -> headers.setBearerAuth(admin.accessToken()))
                    .retrieve()
                    .body(Republished.class);
            return result == null ? 0 : result.republished();
        } catch (RestClientException e) {
            throw new SourceUnavailableException(serviceOf(index), e);
        }
    }

    private RestClient clientOf(SearchedIndex index) {
        return index == SearchedIndex.RESTAURANT ? restaurantService : catalogService;
    }

    private static String serviceOf(SearchedIndex index) {
        return index == SearchedIndex.RESTAURANT ? "restaurant-service" : "catalog-service";
    }
}
