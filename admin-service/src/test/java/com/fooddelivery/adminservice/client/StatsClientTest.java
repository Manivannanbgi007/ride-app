package com.fooddelivery.adminservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fooddelivery.adminservice.client.RemoteRecords.AuditReplay;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantPerformance;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.entity.Role;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.adminservice.web.CallerInfo;
import com.fooddelivery.adminservice.web.RequestIdFilter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

/**
 * The calls admin-service makes to the other services' admin APIs, against a local HTTP server: the query, the
 * caller's identity and request id on the way out, the JSON on the way back, and what a failing service becomes.
 */
class StatsClientTest {

    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-07");
    private static final CallerInfo ADMIN = new CallerInfo(7L, Role.PLATFORM_ADMIN, "127.0.0.1", "req-42", "admin-7-token");

    /** Answers per path: status and JSON body. */
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private StatsClient statsClient;
    private PlatformClient platformClient;

    private record Answer(int status, String json) {
    }

    private record Received(String method, URI uri, Headers headers) {
    }

    @BeforeEach
    void startServices() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI(), exchange.getRequestHeaders()));
            Answer answer = answers.getOrDefault(exchange.getRequestURI().getPath(), new Answer(404, "{}"));
            byte[] body = answer.json().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(answer.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        // Every service at the same local address: the path tells them apart
        AppProperties.RemoteService local = new AppProperties.RemoteService(
                "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(2));
        AppProperties appProperties = new AppProperties("Asia/Kolkata", Duration.ofSeconds(5),
                new AppProperties.Services(local, local, local, local, local, local, local, local, local));
        RemoteCalls remoteCalls = new RemoteCalls(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(10))
                        .retryOnException(new ConnectionFailure()).build()),
                BulkheadRegistry.ofDefaults());
        platformClient = new PlatformClient(RestClient.builder(), appProperties, remoteCalls);
        statsClient = new StatsClient(platformClient);
    }

    @AfterEach
    void stopServices() {
        server.stop(0);
        MDC.clear();
    }

    @Test
    void asksAsTheCallingAdminForThePeriodAndReadsTheAnswer() {
        answers.put("/api/v1/admin/orders/stats", new Answer(200, """
                {"fromDate": "2026-10-01", "toDate": "2026-10-07", "currency": "INR", "orders": 12, "completed": 9,
                 "cancelled": 2, "inProgress": 1, "revenue": 3540.50, "discounts": 100.00, "averageOrderValue": 393.39,
                 "byStatus": {"DELIVERED": 9, "CANCELLED": 2, "PLACED": 1},
                 "byPaymentMethod": {"UPI": {"orders": 8, "completed": 7, "revenue": 2800.00}},
                 "perDay": [{"date": "2026-10-07", "orders": 3, "completed": 2, "cancelled": 1, "revenue": 700.00}],
                 "overall": {"orders": 900, "completed": 850, "inProgress": 1, "revenue": 300000.00}}
                """));
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "req-42");

        OrderStats stats = statsClient.orderStats(FROM, TO, ADMIN);

        assertThat(stats.orders()).isEqualTo(12);
        assertThat(stats.revenue()).isEqualByComparingTo("3540.50");
        assertThat(stats.byPaymentMethod().get("UPI").completed()).isEqualTo(7);
        assertThat(stats.perDay()).singleElement().satisfies(day -> assertThat(day.date()).isEqualTo(TO));
        Received request = received.getFirst();
        assertThat(request.uri().getQuery()).isEqualTo("fromDate=2026-10-01&toDate=2026-10-07");
        assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer admin-7-token");
        assertThat(request.headers().containsKey("X-User-Id")).isFalse();
        assertThat(request.headers().getFirst("X-Request-Id")).isEqualTo("req-42");
    }

    @Test
    void rankingsAskForTheirLength() {
        answers.put("/api/v1/admin/orders/stats/restaurants", new Answer(200, """
                {"fromDate": "2026-10-01", "toDate": "2026-10-07", "currency": "INR", "restaurantsWithOrders": 1,
                 "topByRevenue": [{"restaurantId": 3, "restaurantName": "Spice Hub", "orders": 4, "completed": 4,
                                   "cancelled": 0, "revenue": 1200.00, "cancellationRate": 0.0}],
                 "lowestByRevenue": [], "mostCancellations": []}
                """));

        RestaurantPerformance performance = statsClient.restaurantPerformance(FROM, TO, 5, ADMIN);

        assertThat(performance.topByRevenue()).extracting(RemoteRecords.RestaurantRow::restaurantName)
                .containsExactly("Spice Hub");
        assertThat(received.getFirst().uri().getQuery()).isEqualTo("fromDate=2026-10-01&toDate=2026-10-07&limit=5");
    }

    @Test
    void anErrorAnswerMeansTheServiceIsUnavailable() {
        answers.put("/api/v1/admin/payments/summary", new Answer(500, "{\"title\": \"Internal Server Error\"}"));

        assertThatThrownBy(() -> statsClient.paymentSummary(FROM, TO, ADMIN))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("payment-service");
    }

    @Test
    void aStoppedServiceMeansTheServiceIsUnavailable() {
        server.stop(0);

        assertThatThrownBy(() -> statsClient.deliveryReport(FROM, TO, ADMIN))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("delivery-service");
    }

    @Test
    void internalCallsArePostsWithoutAnAdminIdentity() {
        answers.put("/api/v1/internal/audit-logs/events/republish",
                new Answer(200, "{\"service\": \"order-service\", \"republished\": 42}"));

        AuditReplay replay = platformClient.postInternal(PlatformService.ORDER,
                "/api/v1/internal/audit-logs/events/republish", AuditReplay.class);

        assertThat(replay.republished()).isEqualTo(42);
        assertThat(received.getFirst().method()).isEqualTo("POST");
        assertThat(received.getFirst().headers().containsKey("Authorization")).isFalse();
    }
}
