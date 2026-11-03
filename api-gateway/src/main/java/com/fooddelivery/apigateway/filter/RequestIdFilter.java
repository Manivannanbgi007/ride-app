package com.fooddelivery.apigateway.filter;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Makes sure every request entering the platform has an X-Request-Id.
 * <p>
 * The id is forwarded to the downstream service and returned to the caller,
 * so one business request can be followed across the logs of all services.
 * A WebFilter (instead of a gateway GlobalFilter) is used so that the header
 * is also present on gateway-local responses such as actuator and errors.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    // Accept a caller-supplied id only if it is short and safe to put in logs.
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange.getRequest());

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
                .build();

        // Set just before the response is committed, so downstream headers cannot overwrite it.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });

        // Remembered as the request came in: a circuit breaker fallback forwards to /fallback/..., and its answer
        // still names the path the client asked for
        ServerWebExchangeUtils.addOriginalRequestUrl(exchange, exchange.getRequest().getURI());

        long startNanos = System.nanoTime();

        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> log.info("requestId={} method={} path={} status={} durationMs={}",
                        requestId,
                        request.getMethod(),
                        request.getPath().value(),
                        exchange.getResponse().getStatusCode(),
                        (System.nanoTime() - startNanos) / 1_000_000));
    }

    private String resolveRequestId(ServerHttpRequest request) {
        String incomingRequestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);

        if (incomingRequestId != null && VALID_REQUEST_ID.matcher(incomingRequestId).matches()) {
            return incomingRequestId;
        }

        return UUID.randomUUID().toString();
    }
}
