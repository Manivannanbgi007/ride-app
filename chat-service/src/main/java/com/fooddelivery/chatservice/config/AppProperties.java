package com.fooddelivery.chatservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param userService         user-service: name and role of a user (internal API)
 * @param notificationService notification-service: who is online (internal API; the answer is optional)
 * @param chat                chat rules that an operator may tune
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue RemoteService userService,
                            @DefaultValue RemoteService notificationService,
                            @DefaultValue Chat chat) {

    /** Always set timeouts on remote calls, so a slow service cannot hold a request thread for long. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("5s") Duration readTimeout) {
    }

    public record Chat(@DefaultValue RateLimit rateLimit) {
    }

    /** A sender may write at most {@code maxMessages} messages in any {@code window}. */
    public record RateLimit(@DefaultValue("30") int maxMessages,
                            @DefaultValue("60s") Duration window) {
    }
}
