package com.fooddelivery.adminservice.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the signed-in user's id (the verified token's subject) into every log line of the request (MDC "userId").
 * Runs right after Spring Security's filters (order -100), when the token has been checked.
 */
@Component
@Order(AuthenticatedUserMdcFilter.AFTER_SPRING_SECURITY)
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {

    static final int AFTER_SPRING_SECURITY = -99;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            MDC.put(RequestIdFilter.MDC_USER_ID, token.getToken().getSubject());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIdFilter.MDC_USER_ID);
        }
    }
}
