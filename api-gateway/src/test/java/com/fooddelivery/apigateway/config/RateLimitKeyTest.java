package com.fooddelivery.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Who a request is counted for: the signed-in user (the token's subject), otherwise the client's address. */
class RateLimitKeyTest {

    private final KeyResolver keyResolver = new RateLimitConfig().userOrClientIpKeyResolver();

    private static MockServerWebExchange fromAddress(String ip) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                .remoteAddress(new InetSocketAddress(ip, 52_000)));
    }

    @Test
    void aSignedInUserHasABudgetOfTheirOwnWhereverTheyAre() {
        Principal user = new UsernamePasswordAuthenticationToken("7", "n/a");
        ServerWebExchange first = fromAddress("10.0.0.5").mutate().principal(Mono.just(user)).build();
        ServerWebExchange second = fromAddress("10.0.0.6").mutate().principal(Mono.just(user)).build();

        assertThat(keyResolver.resolve(first).block()).isEqualTo("user:7");
        assertThat(keyResolver.resolve(second).block()).isEqualTo("user:7");
    }

    @Test
    void aVisitorIsCountedByAddress() {
        assertThat(keyResolver.resolve(fromAddress("10.0.0.5")).block()).isEqualTo("ip:10.0.0.5");
    }
}
