package com.fooddelivery.deliverypartnerservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param userService     user-service, asked about partners whose UserRegistered event has not arrived
 * @param deliveryService delivery-service, read once to take over the partners it kept before this service existed
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue RemoteService userService,
                            @DefaultValue RemoteService deliveryService) {

    /** Always set timeouts on remote calls, so a slow service cannot block a request. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("3s") Duration readTimeout) {
    }
}
