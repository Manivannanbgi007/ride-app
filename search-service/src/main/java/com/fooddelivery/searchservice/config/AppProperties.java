package com.fooddelivery.searchservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param restaurantService   restaurant-service: the truth about restaurants (consistency check)
 * @param catalogService      catalog-service: the truth about categories and menu items
 * @param review              review-service: the truth about ratings (rating sync)
 * @param consistencyCheck    the regular comparison of the indexes with MySQL
 * @param ratingSync          the regular copy of the ratings from review-service into the indexes
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue RemoteService restaurantService,
                            @DefaultValue RemoteService catalogService,
                            @DefaultValue RemoteService review,
                            @DefaultValue ConsistencyCheck consistencyCheck,
                            @DefaultValue RatingSync ratingSync) {

    /** Always set timeouts on remote calls, so a slow service cannot hold a request or the scheduler. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("10s") Duration readTimeout) {
    }

    /**
     * @param enabled      compare the indexes with MySQL regularly and log (and count) any difference
     * @param initialDelay wait after start-up (the indexes may still be catching up with Kafka)
     * @param interval     time between two checks
     */
    public record ConsistencyCheck(@DefaultValue("true") boolean enabled,
                                   @DefaultValue("5m") Duration initialDelay,
                                   @DefaultValue("6h") Duration interval) {
    }

    /**
     * @param enabled      copy the ratings from review-service regularly (the admin endpoint works either way)
     * @param initialDelay wait after start-up (the indexes may still be catching up with Kafka)
     * @param interval     time between the end of one run and the start of the next
     */
    public record RatingSync(@DefaultValue("true") boolean enabled,
                             @DefaultValue("30s") Duration initialDelay,
                             @DefaultValue("60s") Duration interval) {
    }
}
