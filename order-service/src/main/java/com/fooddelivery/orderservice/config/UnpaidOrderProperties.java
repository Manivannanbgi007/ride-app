package com.fooddelivery.orderservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Online-payment orders that are never paid (see application.yml).
 *
 * @param expiryEnabled run the job that cancels them
 * @param expireAfter   how long an order may wait for its payment
 * @param checkInterval how often the job looks for expired orders
 */
@ConfigurationProperties(prefix = "app.unpaid-orders")
public record UnpaidOrderProperties(@DefaultValue("true") boolean expiryEnabled,
                                    @DefaultValue("15m") Duration expireAfter,
                                    @DefaultValue("60s") Duration checkInterval) {
}
