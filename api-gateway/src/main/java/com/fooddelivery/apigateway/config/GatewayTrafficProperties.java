package com.fooddelivery.apigateway.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The traffic settings per microservice ("app.gateway" in application.yml). The routes read the numbers from there
 * themselves; this class checks them when the gateway starts, so a wrong value stops the start with a clear message
 * instead of causing odd behaviour later (for example every request refused with 429, or a timeout that never fires).
 *
 * @param services             settings per microservice, by name (user-service, order-service, ...)
 * @param responseTimeoutLimit the circuit breakers' overall time limit (CircuitBreakerConfig): a safety net, so every
 *                             service's response-timeout must be shorter
 */
@ConfigurationProperties(prefix = "app.gateway")
public record GatewayTrafficProperties(Map<String, ServiceTraffic> services,
                                       @DefaultValue("10s") Duration responseTimeoutLimit) {

    /** More attempts only add load to a service that is already in trouble. */
    static final int MAX_RETRIES = 3;

    public GatewayTrafficProperties {
        services = services == null ? Map.of() : Map.copyOf(services);
        for (Map.Entry<String, ServiceTraffic> service : services.entrySet()) {
            service.getValue().check(service.getKey(), responseTimeoutLimit);
        }
    }

    /**
     * One microservice's settings; what each one means is described in application.yml.
     *
     * @param requestsPerSecond requests per second per client
     * @param burst             requests per client at once
     * @param connectTimeout    milliseconds to open a connection
     * @param responseTimeout   milliseconds to wait for the answer
     * @param retries           extra attempts of a GET request when the service could not be reached
     */
    public record ServiceTraffic(int requestsPerSecond, int burst, int connectTimeout, int responseTimeout,
                                 int retries) {

        void check(String service, Duration responseTimeoutLimit) {
            long limit = responseTimeoutLimit.toMillis();
            require(requestsPerSecond >= 1, service, "requests-per-second must be at least 1");
            require(burst >= requestsPerSecond, service, "burst must be at least requests-per-second");
            require(connectTimeout >= 1, service, "connect-timeout must be at least 1 (ms)");
            require(responseTimeout >= 1 && responseTimeout < limit, service,
                    "response-timeout must be between 1 and " + (limit - 1) + " ms (below response-timeout-limit)");
            require(retries >= 1 && retries <= MAX_RETRIES, service, "retries must be between 1 and " + MAX_RETRIES);
            // An unreachable service: every attempt may wait the whole connect-timeout
            require((long) connectTimeout * (retries + 1) < limit, service,
                    "connect-timeout x (retries + 1) must stay below response-timeout-limit (" + limit + " ms)");
        }

        private static void require(boolean valid, String service, String rule) {
            if (!valid) {
                throw new IllegalArgumentException("app.gateway.services." + service + ": " + rule);
            }
        }
    }
}
