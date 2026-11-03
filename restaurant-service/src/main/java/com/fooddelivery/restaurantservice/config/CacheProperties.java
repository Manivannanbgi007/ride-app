package com.fooddelivery.restaurantservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param ttl how long a cached restaurant page is kept. Changes are visible at once anyway (see RestaurantCache);
 *            the TTL only frees memory and limits how long a change made while Redis was unreachable can be hidden.
 */
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(@DefaultValue("10m") Duration ttl) {
}
