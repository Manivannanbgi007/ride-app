package com.fooddelivery.catalogservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param restaurantService where and how to call restaurant-service
 * @param menu              menu limits
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue RestaurantService restaurantService,
                            @DefaultValue Menu menu) {

    /** Always set timeouts on remote calls, so a slow service cannot block request threads. */
    public record RestaurantService(@DefaultValue("http://localhost:8082") String baseUrl,
                                    @DefaultValue("2s") Duration connectTimeout,
                                    @DefaultValue("3s") Duration readTimeout) {
    }

    public record Menu(@DefaultValue("500") int maxItemsPerRestaurant) {
    }
}
