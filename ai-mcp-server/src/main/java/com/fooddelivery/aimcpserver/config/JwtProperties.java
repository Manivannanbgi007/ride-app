package com.fooddelivery.aimcpserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the caller's access token is checked (see application.yml app.jwt).
 *
 * @param jwkSetUri where user-service publishes its public key (fetched once, then cached)
 * @param issuer    the "iss" every token must carry
 * @param audience  the "aud" every token must carry
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String jwkSetUri, String issuer, String audience) {
}
