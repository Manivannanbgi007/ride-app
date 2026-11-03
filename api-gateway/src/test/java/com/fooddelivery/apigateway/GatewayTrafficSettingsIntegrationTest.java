package com.fooddelivery.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * The traffic settings per service (app.gateway.services in application.yml): every service has its own request
 * budget, and only GET requests are sent again when the service could not be reached.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayTrafficSettingsIntegrationTest {

    private static final int TOO_MANY_REQUESTS = 429;

    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);
    private static final StubService searchService = new StubService(0);
    private static final StubService catalogService = new StubService(0);
    // Closes the first two connections without answering, then answers
    private static final StubService notificationService = new StubService(2);
    // Never answers: closes every connection
    private static final StubService promotionService = new StubService(Integer.MAX_VALUE);

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        redis.start();
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // No OTLP exporter in tests (there is no Tempo); tracing export stays on for W3C propagation
        registry.add("management.tracing.export.otlp.enabled", () -> false);
        // A tiny budget for search-service only: 2 requests at once, then 1 per second
        registry.add("app.gateway.services.search-service.requests-per-second", () -> 1);
        registry.add("app.gateway.services.search-service.burst", () -> 2);
        registry.add("SEARCH_SERVICE_URL", searchService::url);
        registry.add("CATALOG_SERVICE_URL", catalogService::url);
        registry.add("NOTIFICATION_SERVICE_URL", notificationService::url);
        registry.add("PROMOTION_SERVICE_URL", promotionService::url);
    }

    @AfterAll
    static void stop() {
        searchService.stop();
        catalogService.stop();
        notificationService.stop();
        promotionService.stop();
        redis.stop();
    }

    @Test
    void everyServiceHasItsOwnRequestBudget() {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            statuses.add(webTestClient.get().uri("/api/v1/search/restaurants?q=pizza").exchange()
                    .expectHeader().valueEquals("X-RateLimit-Burst-Capacity", "2")
                    .returnResult(Void.class).getStatus().value());
        }
        assertThat(statuses).startsWith(200, 200).contains(TOO_MANY_REQUESTS);
        assertThat(searchService.calls.get()).isEqualTo(statuses.stream().filter(status -> status == 200).count());

        // catalog-service is not affected, and keeps its own numbers from application.yml
        webTestClient.get().uri("/api/v1/categories").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Burst-Capacity", "80")
                .expectHeader().valueEquals("X-RateLimit-Replenish-Rate", "40");
    }

    @Test
    void aGetIsSentAgainWhenTheConnectionWasClosedBeforeTheAnswer() {
        webTestClient.get().uri("/api/v1/notifications").exchange()
                .expectStatus().isOk();

        // The first attempt and both retries (application.yml: retries: 2)
        assertThat(notificationService.calls.get()).isEqualTo(3);
    }

    @Test
    void aPostIsNeverSentAgain() {
        webTestClient.post().uri("/api/v1/coupons/validate").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.title").isEqualTo("Service unavailable");

        assertThat(promotionService.calls.get()).isEqualTo(1);
    }

    /** A fake service that drops the first {@code dropFirst} connections without answering, then answers 200. */
    private static final class StubService {

        private final AtomicInteger calls = new AtomicInteger();
        private final HttpServer server;

        StubService(int dropFirst) {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/", exchange -> {
                    if (calls.incrementAndGet() <= dropFirst) {
                        // Closing before any response header was sent closes the connection
                        exchange.close();
                        return;
                    }
                    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
                server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                server.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }
    }
}
