package com.fooddelivery.notificationservice.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone  time zone of "today" in the admin summary
 * @param userService       user-service, asked for the email address and phone number of a recipient
 * @param restaurantService restaurant-service, asked who owns a restaurant
 * @param notifications     sending and retrying
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue RemoteService userService,
                            @DefaultValue RemoteService restaurantService,
                            @DefaultValue Notifications notifications) {

    /** Always set timeouts on remote calls, so a slow service cannot block the dispatcher. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("3s") Duration readTimeout) {
    }

    /**
     * @param from            sender of the emails
     * @param dispatchEnabled run the dispatcher (switched off in tests that run it themselves)
     * @param batchSize       notifications taken per dispatcher run
     * @param maxAttempts     after this many failed attempts a notification is FAILED (an admin can resend it)
     * @param retryDelays     wait before attempt 2, 3, ... (the last delay repeats)
     * @param lease           how long a notification is reserved for the dispatcher that is sending it
     */
    public record Notifications(@DefaultValue("Food Delivery <no-reply@fooddelivery.local>") String from,
                                @DefaultValue("true") boolean dispatchEnabled,
                                @DefaultValue("20") int batchSize,
                                @DefaultValue("5") int maxAttempts,
                                @DefaultValue({"10s", "1m", "5m", "15m"}) List<Duration> retryDelays,
                                @DefaultValue("2m") Duration lease) {
    }
}
