package com.fooddelivery.apigateway;

import static com.fooddelivery.apigateway.filter.RequestIdFilter.REQUEST_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Starts the real gateway, small fake downstream services (JDK HttpServer) and a real Redis (rate limiter),
 * then checks routing, the headers the gateway adds, and rate limiting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // A tiny login budget to see the limiter work (the other routes keep their budgets from application.yml)
        "app.rate-limit.auth-requests-per-minute=3"})
@AutoConfigureWebTestClient
class GatewayRoutingIntegrationTest {

    // Started in static fields so they run before the Spring context reads their ports.
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);
    private static final FakeService userService = new FakeService("user-service");
    private static final FakeService restaurantService = new FakeService("restaurant-service");
    private static final FakeService catalogService = new FakeService("catalog-service");
    private static final FakeService orderService = new FakeService("order-service");
    private static final FakeService paymentService = new FakeService("payment-service");
    private static final FakeService notificationService = new FakeService("notification-service");
    private static final FakeService deliveryService = new FakeService("delivery-service");
    private static final FakeService deliveryPartnerService = new FakeService("delivery-partner-service");
    private static final FakeService searchService = new FakeService("search-service");
    private static final FakeService promotionService = new FakeService("promotion-service");
    private static final FakeService reviewService = new FakeService("review-service");
    private static final FakeService chatService = new FakeService("chat-service");
    private static final FakeService assistantService = new FakeService("ai-mcp-client");
    private static final FakeService adminService = new FakeService("admin-service");

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void routeToFakeServices(DynamicPropertyRegistry registry) {
        redis.start();
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // No OTLP exporter in tests (there is no Tempo); tracing export stays on for W3C propagation
        registry.add("management.tracing.export.otlp.enabled", () -> false);
        // Access tokens of the tests are signed with TestJwts' key
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwts::jwksUri);
        registry.add("USER_SERVICE_URL", userService::url);
        registry.add("RESTAURANT_SERVICE_URL", restaurantService::url);
        registry.add("CATALOG_SERVICE_URL", catalogService::url);
        registry.add("ORDER_SERVICE_URL", orderService::url);
        registry.add("PAYMENT_SERVICE_URL", paymentService::url);
        registry.add("NOTIFICATION_SERVICE_URL", notificationService::url);
        registry.add("DELIVERY_SERVICE_URL", deliveryService::url);
        registry.add("DELIVERY_PARTNER_SERVICE_URL", deliveryPartnerService::url);
        registry.add("SEARCH_SERVICE_URL", searchService::url);
        registry.add("PROMOTION_SERVICE_URL", promotionService::url);
        registry.add("REVIEW_SERVICE_URL", reviewService::url);
        registry.add("CHAT_SERVICE_URL", chatService::url);
        registry.add("AI_MCP_CLIENT_URL", assistantService::url);
        registry.add("ADMIN_SERVICE_URL", adminService::url);
    }

    @AfterAll
    static void stopFakeServices() {
        userService.stop();
        restaurantService.stop();
        catalogService.stop();
        orderService.stop();
        paymentService.stop();
        notificationService.stop();
        deliveryService.stop();
        deliveryPartnerService.stop();
        searchService.stop();
        promotionService.stop();
        reviewService.stop();
        chatService.stop();
        assistantService.stop();
        adminService.stop();
        redis.stop();
    }

    @Test
    void loginAttemptsAreLimitedPerClientWithoutAffectingOtherRoutes() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            webTestClient.post().uri("/api/v1/auth/login")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists("X-RateLimit-Remaining");
        }
        webTestClient.post().uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Browsing has its own budget
        webTestClient.get().uri("/api/v1/users/42").exchange().expectStatus().isOk();
    }

    @Test
    void sessionRefreshAndSignOutHaveABudgetOfTheirOwn() {
        // More refreshes than the whole login budget (3 in this test): they never use up the login attempts
        for (int refresh = 1; refresh <= 5; refresh++) {
            webTestClient.post().uri("/api/v1/auth/refresh")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists("X-RateLimit-Remaining");
        }
        webTestClient.post().uri("/api/v1/auth/logout")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("user-service");

        assertThat(userService.receivedPath.get()).isEqualTo("/api/v1/auth/logout");
    }

    @Test
    void theWebAppMaySendAndReceiveTheRefreshCookie() {
        webTestClient.options().uri("/api/v1/auth/refresh")
                .header("Origin", "http://localhost:5180")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5180")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");

        // Another website's page gets no permission
        webTestClient.options().uri("/api/v1/auth/refresh")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void routesUserRequestToUserServiceWithGeneratedRequestId() {
        webTestClient.get().uri("/api/v1/users/42")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(REQUEST_ID_HEADER)
                .expectBody().jsonPath("$.service").isEqualTo("user-service");

        assertThat(userService.receivedPath.get()).isEqualTo("/api/v1/users/42");
        assertThat(userService.receivedRequestId.get()).isNotBlank();
    }

    @Test
    void forwardsRequestIdSentByCaller() {
        webTestClient.get().uri("/api/v1/admin/users")
                .header(REQUEST_ID_HEADER, "abc-123")
                .header("Authorization", TestJwts.bearer(1, "PLATFORM_ADMIN"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(REQUEST_ID_HEADER, "abc-123");

        assertThat(userService.receivedRequestId.get()).isEqualTo("abc-123");
    }

    @Test
    void addsClientAddressAsXForwardedFor() {
        webTestClient.get().uri("/api/v1/users/42")
                .exchange()
                .expectStatus().isOk();

        // The test client connects from loopback, which is in the default trusted-proxies list.
        assertThat(userService.receivedForwardedFor.get()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }

    /** Every API goes to the service that owns its data (menus and restaurant orders although under /api/v1/restaurants). */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/api/v1/restaurants/5/menu,                  catalog-service",
            "/api/v1/restaurants/mine/5/menu-items,       catalog-service",
            "/api/v1/restaurants/mine/5/menu-items/9/price, catalog-service",
            "/api/v1/categories,                          catalog-service",
            "/api/v1/admin/menu-items,                    catalog-service",
            "/api/v1/restaurants,                         restaurant-service",
            "/api/v1/restaurants/5,                       restaurant-service",
            "/api/v1/restaurants/mine/5,                  restaurant-service",
            "/api/v1/restaurants/mine/5/operating-hours,  restaurant-service",
            "/api/v1/admin/restaurants,                   restaurant-service",
            "/api/v1/restaurants/mine/5/orders,           order-service",
            "/api/v1/restaurants/mine/5/orders/42/accept, order-service",
            "/api/v1/cart/items/9,                        order-service",
            "/api/v1/orders/42,                           order-service",
            "/api/v1/admin/orders,                        order-service",
            "/api/v1/payments/7,                          payment-service",
            "/api/v1/admin/refunds,                       payment-service",
            "/api/v1/notifications,                       notification-service",
            "/api/v1/notifications/unread-count,          notification-service",
            "/api/v1/admin/notifications,                 notification-service",
            "/api/v1/delivery/partners/me,                delivery-partner-service",
            "/api/v1/delivery/partners/me/availability,   delivery-service",
            "/api/v1/delivery/deliveries/mine/current,    delivery-service",
            "/api/v1/delivery/orders/42,                  delivery-service",
            "/api/v1/admin/delivery-partners,             delivery-partner-service",
            "/api/v1/admin/delivery-partners/7,           delivery-partner-service",
            "/api/v1/admin/delivery-partners/7/activate,  delivery-partner-service",
            "/api/v1/admin/delivery-partners/7/deactivate, delivery-partner-service",
            "/api/v1/admin/delivery-partners/7/audit-logs, delivery-partner-service",
            "/api/v1/admin/delivery-partners/availability, delivery-service",
            "/api/v1/admin/delivery-partners/stats,       delivery-service",
            "/api/v1/admin/delivery-partners/7/deliveries, delivery-service",
            "/api/v1/admin/deliveries/delayed,            delivery-service",
            "/api/v1/search/foods,                        search-service",
            "/api/v1/search/suggest,                      search-service",
            "/api/v1/admin/search/indexes,                search-service",
            "/api/v1/coupons,                             promotion-service",
            "/api/v1/admin/coupons/3/redemptions,         promotion-service",
            "/api/v1/reviews/orders/9,                    review-service",
            "/api/v1/ratings/restaurants?ids=1,              review-service",
            "/api/v1/ratings/menu-items/5,                review-service",
            "/api/v1/ratings/restaurants/3/reviews,       review-service",
            "/api/v1/restaurants/mine/4/deliveries/partners, delivery-service",
            "/api/v1/chat/messages,                       chat-service",
            "/api/v1/assistant/chat,                      ai-mcp-client",
            "/api/v1/assistant/actions/abc/confirm,       ai-mcp-client",
            "/api/v1/admin/chat/conversations/4/messages, chat-service",
            "/api/v1/admin/reviews/featured-dishes,       review-service",
            "/ws/v1/live,                                 notification-service",
            "/api/v1/admin/dashboard,                     admin-service",
            "/api/v1/admin/reports/revenue,               admin-service",
            "/api/v1/admin/calendar,                      admin-service",
            "/api/v1/admin/audit-logs,                    admin-service",
            "/api/v1/admin/orders/5/audit-logs,           order-service"
    })
    void routesEachPathToTheServiceThatOwnsTheData(String path, String expectedService) {
        webTestClient.get().uri(path)
                // The admin APIs need an administrator's token at the gateway
                .headers(headers -> {
                    if (path.startsWith("/api/v1/admin/")) {
                        headers.set("Authorization", TestJwts.bearer(1, "PLATFORM_ADMIN"));
                    }
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo(expectedService);
    }

    @Test
    void internalServiceApisAreNotExposed() {
        webTestClient.get().uri("/api/v1/internal/users/42")
                .header("Authorization", TestJwts.bearer(1, "PLATFORM_ADMIN"))
                .exchange()
                .expectStatus().isForbidden();

        assertThat(userService.receivedPath.get()).isNotEqualTo("/api/v1/internal/users/42");
    }

    @Test
    void returnsNotFoundForPathWithoutRoute() {
        webTestClient.get().uri("/api/v1/unknown")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists(REQUEST_ID_HEADER);
    }

    @Test
    void healthEndpointIsUp() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    /** A tiny HTTP server that answers {"service": name} and remembers what it received. */
    private static final class FakeService {

        private final HttpServer server;
        private final AtomicReference<String> receivedPath = new AtomicReference<>();
        private final AtomicReference<String> receivedRequestId = new AtomicReference<>();
        private final AtomicReference<String> receivedForwardedFor = new AtomicReference<>();

        FakeService(String name) {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/", exchange -> {
                    receivedPath.set(exchange.getRequestURI().getPath());
                    receivedRequestId.set(exchange.getRequestHeaders().getFirst(REQUEST_ID_HEADER));
                    receivedForwardedFor.set(exchange.getRequestHeaders().getFirst("X-Forwarded-For"));

                    byte[] body = ("{\"service\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
                server.start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start fake " + name, e);
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
