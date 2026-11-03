package com.fooddelivery.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * The gateway as the first line of defence (phase 18): a token, when sent, must be valid; the admin APIs need an
 * administrator; identity headers sent by a client never reach a service (they are written from the verified token);
 * the gateway's own refusals carry CORS headers and the requestId. Real gateway, real Redis, a fake user-service, and
 * tokens signed like user-service signs them (TestJwts).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityIntegrationTest {

    private static final String WEB_APP = "http://localhost:5180";

    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);
    private static final RecordingService userService = new RecordingService();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        redis.start();
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("management.tracing.export.otlp.enabled", () -> false);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwts::jwksUri);
        registry.add("USER_SERVICE_URL", userService::url);
    }

    @AfterAll
    static void stop() {
        userService.stop();
        redis.stop();
    }

    @BeforeEach
    void forgetTheLastRequest() {
        userService.received.set(null);
    }

    @Test
    void theAdminApisNeedAToken() {
        webTestClient.get().uri("/api/v1/admin/users")
                .header("Origin", WEB_APP)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectHeader().valueEquals("Access-Control-Allow-Origin", WEB_APP)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Authentication required")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.requestId").isNotEmpty();

        assertThat(userService.received.get()).isNull();
    }

    @Test
    void theAdminApisAreForAdministratorsOnly() {
        webTestClient.get().uri("/api/v1/admin/users")
                .header("Authorization", TestJwts.bearer(7, "CUSTOMER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.title").isEqualTo("Forbidden");

        assertThat(userService.received.get()).isNull();
    }

    @Test
    void theGatewaysRoutesAreForAdministratorsOnlyWhileHealthStaysOpen() {
        // Monitoring needs no token
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        // The routes name every service's address
        webTestClient.get().uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri("/actuator/gateway/routes")
                .header("Authorization", TestJwts.bearer(7, "CUSTOMER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.title").isEqualTo("Forbidden");
        webTestClient.get().uri("/actuator/gateway/routes")
                .header("Authorization", TestJwts.bearer(1, "PLATFORM_ADMIN"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void anAdministratorIsLetThroughWithTheirToken() {
        String token = TestJwts.token(1, "PLATFORM_ADMIN");

        webTestClient.get().uri("/api/v1/admin/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        Headers received = userService.received.get();
        // The service reads the caller from the token only (it checks it again); the gateway adds no identity of its own
        assertThat(received.getFirst("Authorization")).isEqualTo("Bearer " + token);
        assertThat(received.containsKey("X-User-Id")).isFalse();
        assertThat(received.containsKey("X-User-Role")).isFalse();
    }

    @Test
    void identityHeadersOfTheClientNeverReachAService() {
        // Without a token: removed
        webTestClient.get().uri("/api/v1/users/me")
                .header("X-User-Id", "1")
                .header("X-User-Role", "PLATFORM_ADMIN")
                .header("X-Service-Token", "guessed")
                .exchange()
                .expectStatus().isOk();
        Headers anonymous = userService.received.get();
        assertThat(anonymous.containsKey("X-User-Id")).isFalse();
        assertThat(anonymous.containsKey("X-User-Role")).isFalse();
        assertThat(anonymous.containsKey("X-Service-Token")).isFalse();

        // With a customer's token: still removed (the token alone says who the caller is)
        String bearer = TestJwts.bearer(7, "CUSTOMER");
        webTestClient.get().uri("/api/v1/users/me")
                .header("Authorization", bearer)
                .header("X-User-Id", "1")
                .header("X-User-Role", "PLATFORM_ADMIN")
                .exchange()
                .expectStatus().isOk();
        Headers customer = userService.received.get();
        assertThat(customer.containsKey("X-User-Id")).isFalse();
        assertThat(customer.containsKey("X-User-Role")).isFalse();
        assertThat(customer.getFirst("Authorization")).isEqualTo(bearer);
    }

    @Test
    void aTokenThatIsSentMustBeValidEvenOnAPublicPage() {
        List<String> refused = List.of(
                TestJwts.tokenSignedByAnotherKey(7, "CUSTOMER"),
                TestJwts.token(7, "CUSTOMER", claims -> claims.issuedAt(Instant.now().minusSeconds(1_200))
                        .expiresAt(Instant.now().minusSeconds(300))),
                TestJwts.token(7, "CUSTOMER", claims -> claims.issuer("https://somebody.else")),
                TestJwts.token(7, "CUSTOMER", claims -> claims.audience(List.of("another-api"))),
                // "alg": "none": no signature at all
                "eyJhbGciOiJub25lIn0.eyJzdWIiOiI3Iiwicm9sZSI6IlBMQVRGT1JNX0FETUlOIn0.",
                "not-a-token");
        for (String token : refused) {
            webTestClient.get().uri("/api/v1/users/me")
                    .header("Authorization", "Bearer " + token)
                    .header("Origin", WEB_APP)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\"")
                    .expectHeader().valueEquals("Access-Control-Allow-Origin", WEB_APP);
        }
        assertThat(userService.received.get()).isNull();
    }

    @Test
    void internalApisAreRefusedEvenToAnAdministrator() {
        webTestClient.get().uri("/api/v1/internal/users/42")
                .header("Authorization", TestJwts.bearer(1, "PLATFORM_ADMIN"))
                .exchange()
                .expectStatus().isForbidden();

        assertThat(userService.received.get()).isNull();
    }

    @Test
    void theBrowserMayAskBeforeSendingAToken() {
        webTestClient.options().uri("/api/v1/admin/users")
                .header("Origin", WEB_APP)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", WEB_APP)
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void anAnswerCarriesTheCorsHeadersOnce() {
        webTestClient.get().uri("/api/v1/users/me")
                .header("Origin", WEB_APP)
                .header("Authorization", TestJwts.bearer(7, "CUSTOMER"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values("Access-Control-Allow-Origin", values -> assertThat(values).containsExactly(WEB_APP));
    }

    /** Plays user-service: answers 200 and keeps the headers of the last request. */
    private static final class RecordingService {

        private final HttpServer server;
        private final AtomicReference<Headers> received = new AtomicReference<>();

        RecordingService() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/", exchange -> {
                    received.set(exchange.getRequestHeaders());
                    byte[] body = "{\"service\":\"user-service\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
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
