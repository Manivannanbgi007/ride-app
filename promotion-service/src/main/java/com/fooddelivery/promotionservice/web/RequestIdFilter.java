package com.fooddelivery.promotionservice.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the X-Request-Id set by the API Gateway (or creates one when the service is called directly),
 * puts it into the logging context (MDC) and returns it in the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));

        MDC.put(MDC_REQUEST_ID, requestId);
        // The user id joins the logs once the access token is checked (AuthenticatedUserMdcFilter)
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // DEBUG: visible with the dev profile, so one request can be followed through every service log.
            log.debug("method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    (System.nanoTime() - startNanos) / 1_000_000);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private String resolveRequestId(String incomingRequestId) {
        if (incomingRequestId != null && VALID_REQUEST_ID.matcher(incomingRequestId).matches()) {
            return incomingRequestId;
        }
        return UUID.randomUUID().toString();
    }
}
