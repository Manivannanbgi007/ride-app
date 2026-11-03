package com.fooddelivery.aimcpserver.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the X-Request-Id set by the caller (or creates one), puts it into the logging context (MDC; the user id joins
 * once the access token is checked, CallerTokenVerifier)
 * and returns it in the response, so one question can be followed through every service log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(ServiceHeaders.REQUEST_ID));
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(ServiceHeaders.REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private String resolveRequestId(String incomingRequestId) {
        if (incomingRequestId != null && VALID_REQUEST_ID.matcher(incomingRequestId).matches()) {
            return incomingRequestId;
        }
        return UUID.randomUUID().toString();
    }
}
