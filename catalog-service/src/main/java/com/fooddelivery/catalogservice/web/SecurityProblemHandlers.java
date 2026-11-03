package com.fooddelivery.catalogservice.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security's own answers in the service's usual RFC 9457 ProblemDetail shape, with the requestId: 401 (no
 * valid token, or no valid service token on an internal API) with {@code WWW-Authenticate: Bearer}, and 403 (a role
 * that may not use this API). The token is never echoed.
 */
@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        bearer.commence(request, response, exception);
        String detail = exception instanceof InsufficientAuthenticationException
                ? "Sign in to use this API."
                : "Your session has expired or is not valid. Please sign in again.";
        write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required", detail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to use this API.");
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
