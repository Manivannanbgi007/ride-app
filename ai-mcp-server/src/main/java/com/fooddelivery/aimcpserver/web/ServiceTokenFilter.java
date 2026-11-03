package com.fooddelivery.aimcpserver.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import com.fooddelivery.aimcpserver.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every /internal and /mcp call must carry the shared secret in X-Service-Token. Only ai-mcp-client (and an MCP host
 * the operator trusts) knows it. The gateway does not route these paths. A missing, wrong or unconfigured token is a 401.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final byte[] expectedDigest;
    private final JsonMapper jsonMapper;

    public ServiceTokenFilter(AppProperties properties, JsonMapper jsonMapper) {
        String token = properties.serviceToken();
        this.expectedDigest = token == null || token.isBlank() ? null : sha256(token);
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/internal/") || path.equals("/mcp") || path.startsWith("/mcp/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String presented = request.getHeader(ServiceHeaders.SERVICE_TOKEN);
        if (expectedDigest != null && presented != null
                && MessageDigest.isEqual(expectedDigest, sha256(presented))) {
            filterChain.doFilter(request, response);
            return;
        }
        // Never log the token itself, only that a call was refused
        log.warn("Refused call without a valid service token: method={} path={}", request.getMethod(),
                request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), Map.of(
                "type", "about:blank",
                "title", "Authentication required",
                "status", 401,
                "detail", "Missing or invalid " + ServiceHeaders.SERVICE_TOKEN + " header.",
                "requestId", String.valueOf(MDC.get(RequestIdFilter.MDC_REQUEST_ID))));
    }

    // Hashing both sides first makes the comparison constant-time and independent of the token length
    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
