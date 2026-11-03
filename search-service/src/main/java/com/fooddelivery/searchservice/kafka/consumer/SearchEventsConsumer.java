package com.fooddelivery.searchservice.kafka.consumer;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.catalog.CategoryChanged;
import com.fooddelivery.events.catalog.MenuItemChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.searchservice.service.SearchIndexer;
import com.fooddelivery.searchservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * One listener for the three searchable topics, so a single consumer thread applies all events of a partition
 * set in the order they arrive (a restaurant change and the indexing of its foods never run side by side in one
 * instance). Thin: each event type goes to {@link SearchIndexer}.
 */
@Component
@RequiredArgsConstructor
@KafkaListener(topics = {Topics.RESTAURANT_CHANGED, Topics.CATEGORY_CHANGED, Topics.MENU_ITEM_CHANGED})
public class SearchEventsConsumer {

    private final SearchIndexer searchIndexer;

    @KafkaHandler
    public void onRestaurantChanged(RestaurantChanged event) {
        apply(event.getRequestId(), () -> searchIndexer.apply(event));
    }

    @KafkaHandler
    public void onCategoryChanged(CategoryChanged event) {
        apply(event.getRequestId(), () -> searchIndexer.apply(event));
    }

    @KafkaHandler
    public void onMenuItemChanged(MenuItemChanged event) {
        apply(event.getRequestId(), () -> searchIndexer.apply(event));
    }

    /** Log lines carry the request id of the change; an overloaded Elasticsearch counts as "try again". */
    private static void apply(String requestId, Runnable work) {
        if (requestId != null) {
            MDC.put(RequestIdFilter.MDC_REQUEST_ID, requestId);
        }
        try {
            work.run();
        } catch (UncategorizedElasticsearchException e) {
            Integer status = e.getStatusCode();
            if (status != null && (status == 429 || status >= 500)) {
                throw new TransientDataAccessResourceException("Elasticsearch cannot take the change right now", e);
            }
            throw e;
        } finally {
            MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
        }
    }
}
