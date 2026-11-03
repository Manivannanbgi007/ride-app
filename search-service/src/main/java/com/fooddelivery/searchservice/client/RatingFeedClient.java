package com.fooddelivery.searchservice.client;

import com.fooddelivery.searchservice.document.SearchedIndex;
import com.fooddelivery.searchservice.dto.response.RatingFeed;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Asks review-service, which owns the ratings (its own MySQL), for the average and count of every rated id. */
@Component
public class RatingFeedClient {

    private static final String SERVICE = "review-service";

    private final RestClient reviewService;

    public RatingFeedClient(@Qualifier("reviewServiceRestClient") RestClient reviewService) {
        this.reviewService = reviewService;
    }

    /** Up to size rated ids above afterId, in id order: restaurants (RESTAURANT) or menu items (FOOD). */
    public RatingFeed ratings(SearchedIndex index, long afterId, int size) {
        String path = switch (index) {
            case RESTAURANT -> "/api/v1/internal/ratings/restaurants";
            case FOOD -> "/api/v1/internal/ratings/menu-items";
            case CATEGORY -> throw new IllegalArgumentException("Categories have no ratings");
        };
        try {
            RatingFeed page = reviewService.get()
                    .uri(uri -> uri.path(path).queryParam("afterId", afterId).queryParam("size", size).build())
                    .retrieve()
                    .body(RatingFeed.class);
            if (page == null) {
                throw new SourceUnavailableException(SERVICE, new IllegalStateException("empty answer"));
            }
            return page;
        } catch (RestClientException e) {
            throw new SourceUnavailableException(SERVICE, e);
        }
    }
}
