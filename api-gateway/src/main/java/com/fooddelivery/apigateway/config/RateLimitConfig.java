package com.fooddelivery.apigateway.config;

import java.net.InetSocketAddress;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Rate limiting with Redis (token buckets shared by every gateway instance). Too many requests get
 * 429 Too Many Requests, with X-RateLimit-* headers telling the client its budget.
 * <ul>
 *   <li>Every route: its service's requests-per-second and burst per client (app.gateway.services in
 *       application.yml); each route has its own budget. A signed-in client is counted by its user id (from the
 *       verified access token), a visitor by IP address.</li>
 *   <li>Login and registration: {@code authRequestsPerMinute} per client.</li>
 *   <li>Session refresh and sign-out: {@code sessionRequestsPerMinute} per client, a budget of their own.</li>
 * </ul>
 * If Redis is unavailable, requests are let through (the gateway never fails because of the limiter).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final int SECONDS_PER_MINUTE = 60;
    /** Only for a route whose RequestRateLimiter sets no numbers (every route in application.yml sets them). */
    private static final int FALLBACK_REQUESTS_PER_SECOND = 20;
    private static final int FALLBACK_BURST = 40;

    /** Used by the RequestRateLimiter filter of every route; the route's args set its numbers. */
    @Bean
    @Primary
    public RedisRateLimiter defaultRateLimiter() {
        return new OutageAwareRedisRateLimiter("general", FALLBACK_REQUESTS_PER_SECOND, FALLBACK_BURST, 1);
    }

    /** Login and registration: N attempts per minute. */
    @Bean
    public RedisRateLimiter authRateLimiter(RateLimitProperties limits) {
        return perMinute("login", limits.authRequestsPerMinute());
    }

    /** Session refresh and sign-out: N requests per minute, counted like the login attempts. */
    @Bean
    public RedisRateLimiter sessionRateLimiter(RateLimitProperties limits) {
        return perMinute("session", limits.sessionRequestsPerMinute());
    }

    /**
     * Who is counted: the signed-in user (the subject of the verified access token: people behind one office or
     * mobile network address keep their own budgets), otherwise the client's IP address as the gateway sees it.
     */
    @Bean
    @Primary
    public KeyResolver userOrClientIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> "user:" + principal.getName())
                .defaultIfEmpty("ip:" + clientIp(exchange.getRequest().getRemoteAddress()));
    }

    /** A separate budget for login and registration, so browsing does not use it up (and the other way round). */
    @Bean
    public KeyResolver authKeyResolver() {
        return exchange -> Mono.just("auth:ip:" + clientIp(exchange.getRequest().getRemoteAddress()));
    }

    /** A separate budget for session refresh and sign-out, so they never use up the login attempts. */
    @Bean
    public KeyResolver sessionKeyResolver() {
        return exchange -> Mono.just("session:ip:" + clientIp(exchange.getRequest().getRemoteAddress()));
    }

    /** Redis down: limiter commands fail at once instead of making every request wait for a timeout. */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisIsDown() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }

    /**
     * N requests per minute: the bucket refills N tokens a second and each request costs 60, so N requests can be made
     * at once and then one every 60/N seconds.
     */
    private static RedisRateLimiter perMinute(String name, int requestsPerMinute) {
        return new OutageAwareRedisRateLimiter(name, requestsPerMinute, (long) requestsPerMinute * SECONDS_PER_MINUTE,
                SECONDS_PER_MINUTE);
    }

    private static String clientIp(InetSocketAddress address) {
        return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
    }
}
