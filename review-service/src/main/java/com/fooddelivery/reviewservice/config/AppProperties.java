package com.fooddelivery.reviewservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone the time zone of the "from" and "to" filters ("to 2026-10-31" means the end of that day there)
 * @param orderService     order-service: tells who ordered what, and whether the order was delivered
 * @param catalogService   catalog-service: current name and picture of a dish (only to make the featured dishes nicer)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue RemoteService orderService,
                            @DefaultValue RemoteService catalogService) {

    /** Always set timeouts on remote calls, so a slow service cannot hold a request thread for long. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("5s") Duration readTimeout) {
    }
}
