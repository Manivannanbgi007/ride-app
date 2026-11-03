package com.fooddelivery.userservice.web;

import java.util.List;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * CSRF protection for the endpoints that use the refresh cookie (login, refresh, logout).
 * <p>
 * Browsers always send an Origin header with these POST requests. If it is not one of our web pages, the request is
 * refused, so another website cannot make a visitor's browser refresh or end their session. Requests without an
 * Origin header come from programs (tests, scripts), not from a web page, and are allowed.
 * SameSite=Strict cookies and the JSON-only request bodies are two more layers of the same protection.
 */
@Slf4j
@Component
public class AllowedOrigins {

    private final List<String> origins;

    public AllowedOrigins(AppProperties appProperties) {
        this.origins = appProperties.auth().allowedOrigins().stream()
                .map(origin -> origin.trim().replaceAll("/+$", ""))
                .toList();
    }

    public void check(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origins.contains(origin)) {
            log.warn("Session request from a web page that is not allowed. origin={}", origin);
            throw new ForbiddenException("Requests from this web page are not allowed.");
        }
    }
}
