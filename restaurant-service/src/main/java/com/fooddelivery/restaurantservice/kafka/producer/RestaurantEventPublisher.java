package com.fooddelivery.restaurantservice.kafka.producer;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.restaurantservice.cache.RestaurantCache;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantAddress;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds a RestaurantChanged event carrying the restaurant's current state and writes it to the
 * transactional outbox, in the caller's transaction; {@link OutboxRelay} publishes it to Kafka.
 * <p>
 * Call it after the change was flushed, so the event carries the new version: consumers keep
 * their own copy of the restaurant and use the version to ignore events older than that copy.
 */
@Component
@RequiredArgsConstructor
public class RestaurantEventPublisher {

    private static final String SUBMITTED = "SUBMITTED";
    private static final String PROFILE_UPDATED = "PROFILE_UPDATED";
    private static final String STATUS_CHANGED = "STATUS_CHANGED";
    private static final String REPUBLISHED = "REPUBLISHED";
    private static final String AGGREGATE_RESTAURANT = "RESTAURANT";

    private final OutboxWriter outboxWriter;
    private final RestaurantCache restaurantCache;
    private final Clock clock;

    public void restaurantSubmitted(Restaurant restaurant) {
        publish(restaurant, SUBMITTED, null);
    }

    public void profileUpdated(Restaurant restaurant) {
        publish(restaurant, PROFILE_UPDATED, null);
    }

    public void statusChanged(Restaurant restaurant, RestaurantStatus previousStatus) {
        publish(restaurant, STATUS_CHANGED, previousStatus);
    }

    /** The current state again, unchanged: consumers that missed events (or a new search index) catch up. */
    public void republished(Restaurant restaurant) {
        publish(restaurant, REPUBLISHED, null);
    }

    private void publish(Restaurant restaurant, String changeType, RestaurantStatus previousStatus) {
        RestaurantChanged event = RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setChangeType(changeType)
                .setRestaurantId(restaurant.getId())
                .setVersion(restaurant.getVersion())
                .setOwnerUserId(restaurant.getOwnerUserId())
                .setName(restaurant.getName())
                .setCity(restaurant.getAddress().city())
                .setStatus(restaurant.getStatus().name())
                .setPreviousStatus(previousStatus == null ? null : previousStatus.name())
                .setReason(restaurant.getStatusReason())
                .setAddress(pickupAddress(restaurant.getAddress()))
                // Since event-contracts 1.4.0: what restaurant search needs
                .setDescription(restaurant.getDescription())
                .setCuisines(List.copyOf(restaurant.getCuisines()))
                .build();

        outboxWriter.append(AGGREGATE_RESTAURANT, restaurant.getId(), Topics.RESTAURANT_CHANGED,
                String.valueOf(restaurant.getId()), event);
        // Every restaurant change passes through here: the cached public page is refreshed after the commit
        restaurantCache.restaurantChanged(restaurant.getId());
    }

    /** Where delivery partners pick orders up (in the event since event-contracts 1.3.0). */
    private static com.fooddelivery.events.restaurant.RestaurantAddress pickupAddress(RestaurantAddress address) {
        return com.fooddelivery.events.restaurant.RestaurantAddress.newBuilder()
                .setLine1(address.line1())
                .setLine2(address.line2())
                .setCity(address.city())
                .setState(address.state())
                .setPostalCode(address.postalCode())
                .setCountry(address.country())
                .setLatitude(address.latitude() == null ? null : address.latitude().doubleValue())
                .setLongitude(address.longitude() == null ? null : address.longitude().doubleValue())
                .build();
    }
}
