package com.fooddelivery.promotionservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone the time zone of coupon dates ("valid until 2026-10-31" means the end of that day there)
 * @param reservations     clean-up of coupon uses that no order was placed with
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue Reservations reservations) {

    /**
     * @param cleanupEnabled run the clean-up job (switched off in tests that run it themselves)
     * @param expireAfter    a reservation with no OrderCreated after this long is given back
     */
    public record Reservations(@DefaultValue("true") boolean cleanupEnabled,
                               @DefaultValue("30m") Duration expireAfter) {
    }
}
