package com.fooddelivery.apigateway.config;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One circuit breaker per target service (the routes' CircuitBreaker filters name it). The gateway only counts what
 * it sees itself: the service could not be reached, or did not answer in time (the route's response-timeout, or at
 * the latest app.gateway.response-timeout-limit, 10 s, the overall limit set here). An error answer from a service
 * is passed on and does not count: a 503 from order-service because promotion-service is down must not cut off
 * every order request at the gateway. While a circuit is open the gateway answers at once (FallbackController).
 */
@Configuration
@EnableConfigurationProperties(GatewayTrafficProperties.class)
public class CircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> serviceCircuitBreakers(
            GatewayTrafficProperties traffic) {
        // A safety net above every route's own response-timeout (checked by GatewayTrafficProperties)
        Duration responseTimeoutLimit = traffic.responseTimeoutLimit();
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            // Judged on the last 10 requests, once at least 5 were made; open at half of them failing
                            .slidingWindowSize(10)
                            .minimumNumberOfCalls(5)
                            .failureRateThreshold(50)
                            // Then answer at once for 10 s; afterwards 3 trial requests decide
                            .waitDurationInOpenState(Duration.ofSeconds(10))
                            .automaticTransitionFromOpenToHalfOpenEnabled(true)
                            .permittedNumberOfCallsInHalfOpenState(3)
                            .build())
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(responseTimeoutLimit).build())
                    .build());
            // One log line per state change: "Circuit for order-service: State transition from CLOSED to OPEN"
            factory.getCircuitBreakerRegistry().getEventPublisher().onEntryAdded(added -> {
                CircuitBreaker circuitBreaker = added.getAddedEntry();
                circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn("Circuit for {}: {}",
                        circuitBreaker.getName(), event.getStateTransition()));
            });
        };
    }
}
