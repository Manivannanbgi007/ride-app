package com.fooddelivery.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The per-minute limits of the sign-in endpoints, per client IP (settings under "app.rate-limit"). The limits of the
 * other routes are set per service: app.gateway.services (GatewayTrafficProperties).
 *
 * @param authRequestsPerMinute    login and registration: attempts per minute (slows down password guessing)
 * @param sessionRequestsPerMinute session refresh and sign-out: requests per minute (the web app refreshes each
 *                                 signed-in area's token every few minutes, and again after a page reload)
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(@DefaultValue("10") int authRequestsPerMinute,
                                  @DefaultValue("30") int sessionRequestsPerMinute) {
}
