package com.fooddelivery.apigateway.filter;

import static com.fooddelivery.apigateway.filter.RequestIdFilter.REQUEST_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private final AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();

    // Simulates the rest of the gateway: remembers what was forwarded and completes the response.
    private final WebFilterChain chain = exchange -> {
        forwardedExchange.set(exchange);
        return exchange.getResponse().setComplete();
    };

    @Test
    void generatesRequestIdWhenCallerDoesNotSendOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders"));

        filter.filter(exchange, chain).block();

        String forwardedId = forwardedRequestId();
        assertThat(UUID.fromString(forwardedId)).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(REQUEST_ID_HEADER)).isEqualTo(forwardedId);
    }

    @Test
    void keepsValidRequestIdSentByCaller() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(REQUEST_ID_HEADER, "abc-123"));

        filter.filter(exchange, chain).block();

        assertThat(forwardedRequestId()).isEqualTo("abc-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(REQUEST_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void replacesUnsafeRequestIdSentByCaller() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(REQUEST_ID_HEADER, "<script>alert(1)</script>"));

        filter.filter(exchange, chain).block();

        String forwardedId = forwardedRequestId();
        assertThat(forwardedId).isNotEqualTo("<script>alert(1)</script>");
        assertThat(UUID.fromString(forwardedId)).isNotNull();
    }

    private String forwardedRequestId() {
        return forwardedExchange.get().getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
    }
}
