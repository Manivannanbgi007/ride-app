package com.fooddelivery.catalogservice.service;

import java.util.Optional;

import com.fooddelivery.catalogservice.cache.MenuCache;
import com.fooddelivery.catalogservice.client.RestaurantServiceClient;
import com.fooddelivery.catalogservice.client.RestaurantSummary;
import com.fooddelivery.catalogservice.entity.RestaurantSnapshot;
import com.fooddelivery.catalogservice.exception.RemoteServiceException;
import com.fooddelivery.catalogservice.repository.RestaurantSnapshotRepository;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where catalog-service looks up a restaurant's owner and status: first its local copy, kept up to date
 * from RestaurantChanged events, and restaurant-service only for restaurants it has not heard of yet
 * (for example ones created before this service consumed events). So menus keep working while
 * restaurant-service is down.
 * <p>
 * Known gap until Phase 8 (outbox): an event lost by the producer leaves the copy outdated
 * until the restaurant's next change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantDirectory {

    private final RestaurantSnapshotRepository restaurantSnapshotRepository;
    private final RestaurantServiceClient restaurantServiceClient;
    private final MenuCache menuCache;

    /**
     * @return the restaurant, or empty if it does not exist
     * @throws RemoteServiceException if it is not known locally and restaurant-service cannot be reached
     */
    public Optional<RestaurantSummary> findRestaurant(Long restaurantId) {
        Optional<RestaurantSnapshot> snapshot = restaurantSnapshotRepository.findById(restaurantId);
        if (snapshot.isPresent()) {
            return snapshot.map(RestaurantDirectory::toSummary);
        }

        Optional<RestaurantSummary> restaurant = restaurantServiceClient.findRestaurant(restaurantId);
        restaurant.ifPresent(this::remember);
        return restaurant;
    }

    /** Applies a RestaurantChanged event. Events not newer than the local copy are ignored. */
    @Transactional
    public void apply(RestaurantChanged event) {
        Optional<Long> localVersion = restaurantSnapshotRepository.findById(event.getRestaurantId())
                .map(RestaurantSnapshot::getSourceVersion);
        if (localVersion.isPresent() && localVersion.get() >= event.getVersion()) {
            log.info("RestaurantChanged ignored: local copy is as new or newer. restaurantId={} eventVersion={} localVersion={}",
                    event.getRestaurantId(), event.getVersion(), localVersion.get());
            return;
        }

        restaurantSnapshotRepository.upsertIfNewer(event.getRestaurantId(), event.getOwnerUserId(), event.getName(),
                event.getStatus(), event.getVersion());
        log.info("Restaurant copy updated. restaurantId={} status={} version={} changeType={}",
                event.getRestaurantId(), event.getStatus(), event.getVersion(), event.getChangeType());
        // The menu shows the restaurant name and is hidden unless the restaurant is ACTIVE
        menuCache.menuChanged(event.getRestaurantId());
    }

    /** Keeps a restaurant fetched over REST, so the next lookup does not need restaurant-service. */
    private void remember(RestaurantSummary restaurant) {
        if (restaurant.version() == null) {
            return;
        }
        try {
            restaurantSnapshotRepository.upsertIfNewer(restaurant.id(), restaurant.ownerUserId(), restaurant.name(),
                    restaurant.status(), restaurant.version());
        } catch (DataAccessException e) {
            // Only a cache miss next time: the lookup itself succeeded.
            log.warn("Could not store the restaurant copy. restaurantId={} reason={}", restaurant.id(), e.getMessage());
        }
    }

    private static RestaurantSummary toSummary(RestaurantSnapshot snapshot) {
        return new RestaurantSummary(snapshot.getRestaurantId(), snapshot.getOwnerUserId(), snapshot.getName(),
                snapshot.getStatus(), snapshot.getSourceVersion());
    }
}
