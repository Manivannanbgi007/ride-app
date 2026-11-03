package com.fooddelivery.restaurantservice.cache;

import java.util.List;
import java.util.function.Supplier;

import com.fooddelivery.restaurantservice.config.CacheProperties;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantDetailsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Caches the public restaurant page (details, cuisines, address, opening hours); MySQL stays the source of truth.
 * "Open now" is not cached: it depends on the clock and is worked out from the cached hours on every request.
 * Any change to a restaurant makes its cached page invisible at once (see {@link RedisJsonCache}).
 * <p>
 * Keys: restaurant:v1:details:{restaurantId}:{generation} ("v1" changes with the shape of the cached JSON).
 */
@Component
@RequiredArgsConstructor
public class RestaurantCache {

    private static final String KEY_PREFIX = "restaurant:v1:details:";

    private final RedisJsonCache cache;
    private final CacheProperties properties;

    /** The cached page, or the one {@code loader} builds from MySQL (then cached). */
    public PublicRestaurantDetailsResponse details(Long restaurantId, Supplier<PublicRestaurantDetailsResponse> loader) {
        return cache.generations(List.of(generation(restaurantId)))
                .map(generations -> {
                    String key = KEY_PREFIX + restaurantId + ":" + generations.getFirst();
                    return cache.get(key, PublicRestaurantDetailsResponse.class).orElseGet(() -> {
                        PublicRestaurantDetailsResponse details = loader.get();
                        cache.put(key, details, properties.ttl());
                        return details;
                    });
                })
                .orElseGet(loader);
    }

    /** The restaurant changed (profile, hours, status): applied when the transaction commits. */
    public void restaurantChanged(Long restaurantId) {
        cache.bumpAfterCommit(generation(restaurantId));
    }

    private static String generation(Long restaurantId) {
        return "restaurant:generation:" + restaurantId;
    }
}
