package com.fooddelivery.aimcpclient.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The answers of Spring Security itself (no token, a token that is not valid, a request that is not allowed), in the
 * same RFC 9457 ProblemDetail shape as every other error of the platform, with the requestId. The standard
 * {@code WWW-Authenticate} header comes from Spring's Bearer token handlers. The token is never echoed.
 */
@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    public SecurityProblemHandlers(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        bearerEntryPoint.commence(request, response, exception);
        String detail = exception instanceof InvalidBearerTokenException
                ? "Your session has expired or is not valid. Please sign in again."
                : "Sign in to use the assistant.";
        write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required", detail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        bearerAccessDenied.handle(request, response, exception);
        write(request, response, HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to do that.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title,
                       String detail) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null) {
            problem.put("requestId", requestId);
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
