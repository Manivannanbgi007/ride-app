package com.fooddelivery.restaurantservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param defaultTimeZone time zone used when a restaurant does not specify one
 * @param userService     where and how to call user-service
 * @param businessTimeZone time zone of the business: statistics count calendar days in it
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String defaultTimeZone,
                            @DefaultValue UserService userService,
                            @DefaultValue("Asia/Kolkata") String businessTimeZone) {

    /**
     * Always set timeouts on remote calls: without them, a slow service can block request threads indefinitely.
     */
    public record UserService(@DefaultValue("http://localhost:8081") String baseUrl,
                              @DefaultValue("2s") Duration connectTimeout,
                              @DefaultValue("3s") Duration readTimeout) {
    }
}
