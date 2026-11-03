package com.fooddelivery.apigateway.config;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fooddelivery.apigateway.filter.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * The gateway's own security answers, in the same RFC 9457 ProblemDetail shape (with the requestId) as every other
 * error of the platform. The token is never echoed.
 * <ul>
 *   <li>401 with {@code WWW-Authenticate: Bearer}: no token where one is needed, or a token that is not valid
 *       (the web app then renews it and asks again).</li>
 *   <li>403: a valid token whose role may not do this.</li>
 *   <li>503: the token cannot be checked, because user-service's public key cannot be read (fail closed).</li>
 * </ul>
 */
@Component
public class GatewaySecurityProblemHandlers
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler, ServerAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityProblemHandlers.class);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return unauthorized(exchange, exception instanceof OAuth2AuthenticationException);
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        ServerWebExchange exchange = webFilterExchange.getExchange();
        if (exception instanceof AuthenticationServiceException) {
            log.warn("Access tokens cannot be checked: {}", exception.getMessage());
            return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                    "Your sign-in cannot be checked right now. Please try again in a moment.");
        }
        return commence(exchange, exception);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        // Without a token the problem is not the role: the caller has to sign in first
        return exchange.getPrincipal().hasElement().flatMap(signedIn -> signedIn
                ? write(exchange, HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to do that.")
                : unauthorized(exchange, false));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, boolean tokenRefused) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                tokenRefused ? "Bearer error=\"invalid_token\"" : "Bearer");
        return write(exchange, HttpStatus.UNAUTHORIZED, "Authentication required", tokenRefused
                ? "Your session has expired or is not valid. Please sign in again."
                : "Sign in to continue.");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String title, String detail) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", exchange.getRequest().getPath().value());
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        if (requestId != null) {
            problem.put("requestId", requestId);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer body = response.bufferFactory()
                .wrap(jsonMapper.writeValueAsString(problem).getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }
}
