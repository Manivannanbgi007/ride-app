package com.fooddelivery.catalogservice.kafka.consumer;

import com.fooddelivery.catalogservice.service.RestaurantDirectory;
import com.fooddelivery.catalogservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the local restaurant copy up to date. A failure is retried, then the event goes to the
 * dead-letter topic (see KafkaConfig), so one bad event never blocks the others.
 */
@Component
@RequiredArgsConstructor
public class RestaurantChangedConsumer {

    private final RestaurantDirectory restaurantDirectory;

    @KafkaListener(topics = Topics.RESTAURANT_CHANGED)
    public void onRestaurantChanged(RestaurantChanged event) {
        // Log lines of this event carry the request id of the request that caused it.
        if (event.getRequestId() != null) {
            MDC.put(RequestIdFilter.MDC_REQUEST_ID, event.getRequestId());
        }
        try {
            restaurantDirectory.apply(event);
        } finally {
            MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
        }
    }
}
