package com.fooddelivery.aimcpserver.security;

import java.util.Locale;

import com.fooddelivery.aimcpserver.exception.ApiException;
import com.fooddelivery.aimcpserver.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * The one place that says who is asking: the caller's access token (Authorization: Bearer) is checked (JwtConfig:
 * signature, expiry, issuer, audience) and its subject and role become the {@link CallerContext}. Nothing the user
 * types, nor anything the model produces, can change it. The token is never logged.
 */
@Slf4j
@Component
public class CallerTokenVerifier {

    private static final String BEARER = "Bearer ";
    private static final String ROLE_CLAIM = "role";

    private final JwtDecoder decoder;

    public CallerTokenVerifier(JwtDecoder callerTokenDecoder) {
        this.decoder = callerTokenDecoder;
    }

    /**
     * @param authorization the Authorization header ("Bearer ..."), or null
     * @param requestId     the request id, carried along for the calls made for the caller
     * @throws ApiException 401 for a missing or invalid token, 503 when user-service's key cannot be read (fail closed)
     */
    public CallerContext verify(String authorization, String requestId) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            throw ApiException.unauthorized("Missing access token.");
        }
        String token = authorization.substring(BEARER.length()).trim();
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (BadJwtException e) {
            log.info("Access token refused: {}", e.getClass().getSimpleName());
            throw ApiException.unauthorized("The access token is not valid.");
        } catch (JwtException e) {
            log.warn("Access tokens cannot be checked right now: {}", e.getClass().getSimpleName());
            throw ApiException.unavailable("The assistant cannot check who you are right now. Please try again.");
        }
        long userId = userIdOf(jwt.getSubject());
        Role role = roleOf(jwt.getClaimAsString(ROLE_CLAIM));
        MDC.put(RequestIdFilter.MDC_USER_ID, String.valueOf(userId));
        return new CallerContext(userId, role, requestId, token);
    }

    private static long userIdOf(String subject) {
        try {
            long userId = Long.parseLong(subject);
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException e) {
            // refused below
        }
        throw ApiException.unauthorized("The access token does not name a user.");
    }

    private static Role roleOf(String claim) {
        try {
            return Role.valueOf(String.valueOf(claim).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("The access token does not carry a known role.");
        }
    }
}
