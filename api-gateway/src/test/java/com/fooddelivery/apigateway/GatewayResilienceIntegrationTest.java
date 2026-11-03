package com.fooddelivery.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * The gateway's circuit breakers: a service that cannot be reached, one that is too slow, and one that answers
 * with errors (passed on, and not held against it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayResilienceIntegrationTest {

    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);
    private static final AtomicInteger slowCalls = new AtomicInteger();
    private static final HttpServer slowService = server(2_000, 200, slowCalls);
    private static final AtomicInteger failingCalls = new AtomicInteger();
    private static final HttpServer failingService = server(0, 503, failingCalls);

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private ReactiveResilience4JCircuitBreakerFactory circuitBreakerFactory;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        redis.start();
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // No OTLP exporter in tests (there is no Tempo); tracing export stays on for W3C propagation
        registry.add("management.tracing.export.otlp.enabled", () -> false);
        // payment-service's answer may take 500 ms (its stub takes 2 s)
        registry.add("app.gateway.services.payment-service.response-timeout", () -> 500);
        // Nobody listens on port 1: every connection is refused
        registry.add("ORDER_SERVICE_URL", () -> "http://localhost:1");
        registry.add("PAYMENT_SERVICE_URL", () -> "http://localhost:" + slowService.getAddress().getPort());
        registry.add("DELIVERY_SERVICE_URL", () -> "http://localhost:" + failingService.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        slowService.stop(0);
        failingService.stop(0);
        redis.stop();
    }

    @Test
    void aServiceThatCannotBeReachedGetsAClear503AndIsThenLeftAlone() {
        for (int i = 0; i < 5; i++) {
            webTestClient.get().uri("/api/v1/orders").exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals("Retry-After", "10")
                    .expectHeader().exists("X-Request-Id")
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Service unavailable")
                    .jsonPath("$.service").isEqualTo("order-service")
                    .jsonPath("$.instance").isEqualTo("/api/v1/orders");
        }
        CircuitBreaker circuit = circuitBreakerFactory.getCircuitBreakerRegistry().circuitBreaker("order-service");
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        webTestClient.get().uri("/api/v1/cart").exchange().expectStatus().isEqualTo(503);
        assertThat(circuit.getMetrics().getNumberOfNotPermittedCalls()).isPositive();
    }

    @Test
    void aServiceThatDoesNotAnswerInTimeGetsA504() {
        webTestClient.get().uri("/api/v1/payments").exchange()
                .expectStatus().isEqualTo(504)
                .expectBody().jsonPath("$.title").isEqualTo("Service timeout");
        assertThat(slowCalls.get()).isEqualTo(1);
    }

    @Test
    void errorAnswersArePassedOnAndDoNotOpenTheCircuit() {
        for (int i = 0; i < 8; i++) {
            webTestClient.get().uri("/api/v1/delivery/orders/42").exchange()
                    .expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.detail").isEqualTo("delivery-service's own answer");
        }
        assertThat(failingCalls.get()).isEqualTo(8);
        assertThat(circuitBreakerFactory.getCircuitBreakerRegistry().circuitBreaker("delivery-service").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @SuppressWarnings("java:S2925") // the stub answers late on purpose (a slow service)
    private static HttpServer server(long delayMillis, int status, AtomicInteger calls) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                try {
                    Thread.sleep(Duration.ofMillis(delayMillis));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = "{\"detail\":\"delivery-service's own answer\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                } catch (IOException e) {
                    // the gateway gave up waiting (timeout test)
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
