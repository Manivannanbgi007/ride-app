package com.fooddelivery.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** The gateway's own security answers: status, WWW-Authenticate and a ProblemDetail body with the requestId. */
class GatewaySecurityProblemHandlersTest {

    private final GatewaySecurityProblemHandlers handlers = new GatewaySecurityProblemHandlers();

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/admin/users").header("X-Request-Id", "rq-1"));
    }

    @Test
    void aRefusedTokenIs401WithInvalidTokenAndTheRequestId() {
        MockServerWebExchange exchange = exchange();

        handlers.commence(exchange, new InvalidBearerTokenException("Jwt expired at 2026-10-14T10:00:00Z")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_token\"");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"title\":\"Authentication required\"", "\"requestId\":\"rq-1\"",
                        "\"instance\":\"/api/v1/admin/users\"")
                .doesNotContain("2026-10-14T10:00:00Z");
    }

    @Test
    void whenThePublicKeyCannotBeReadTheAnswerIs503() {
        MockServerWebExchange exchange = exchange();
        WebFilterChain chain = ignored -> Mono.empty();

        handlers.onAuthenticationFailure(new WebFilterExchange(exchange, chain),
                new AuthenticationServiceException("Couldn't retrieve remote JWK set")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("JWK");
    }

    @Test
    void anyOtherFailedSignInIs401() {
        MockServerWebExchange exchange = exchange();

        handlers.onAuthenticationFailure(new WebFilterExchange(exchange, ignored -> Mono.empty()),
                new InvalidBearerTokenException("bad signature")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aSignedInCallerWithoutTheRightRoleIs403() {
        Principal admin = new UsernamePasswordAuthenticationToken("7", "n/a");
        ServerWebExchange exchange = exchange().mutate().principal(Mono.just(admin)).build();

        handlers.handle(exchange, new AccessDeniedException("role")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().containsHeader("WWW-Authenticate")).isFalse();
    }

    @Test
    void aCallerWithoutATokenIsAskedToSignInInsteadOf403() {
        MockServerWebExchange exchange = exchange();

        handlers.handle(exchange, new AccessDeniedException("role")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
    }
}
