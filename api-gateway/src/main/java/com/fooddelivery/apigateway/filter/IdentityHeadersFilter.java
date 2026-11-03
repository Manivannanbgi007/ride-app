package com.fooddelivery.apigateway.filter;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Headers only the platform itself may send. They are removed from every request a client sends, so a client can
 * never pass itself off as a user or as one of the services: the services know the caller only from the access token
 * (the Authorization header, passed on unchanged and checked again by each service), and the internal service token
 * is sent by the services to each other, never through the gateway.
 * <p>
 * The X-User-Id / X-User-Role headers are not read by anyone any more (they were the identity before phase 18); they
 * are still removed so that no service can ever be tricked by them again.
 */
@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    /** Removed from every request of a client */
    static final List<String> PLATFORM_ONLY_HEADERS = List.of("X-User-Id", "X-User-Role", "X-Service-Token");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> PLATFORM_ONLY_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** Before every other filter of a route, so that nothing after it sees those headers. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
