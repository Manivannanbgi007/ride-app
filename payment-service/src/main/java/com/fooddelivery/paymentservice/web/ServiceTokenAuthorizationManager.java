package com.fooddelivery.paymentservice.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Lets a call into an internal API (/api/v1/internal/**) through only with the platform's shared service token
 * (X-Service-Token, INTERNAL_SERVICE_TOKEN): the other services send it, a browser never has it. Compared in constant
 * time, so the answer time says nothing about how much of a guess was right. A blank configured token lets nothing in.
 */
@Component
public class ServiceTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    public static final String HEADER = "X-Service-Token";

    private final byte[] expected;

    public ServiceTokenAuthorizationManager(@Value("${app.security.internal-service-token}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        String presented = context.getRequest().getHeader(HEADER);
        boolean granted = expected.length > 0 && presented != null
                && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
        return new AuthorizationDecision(granted);
    }
}
