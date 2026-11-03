package com.fooddelivery.aimcpclient.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.exception.AuthenticationFailedException;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import com.fooddelivery.aimcpclient.identity.Role;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Establishes WHO the caller is: the single place that turns the verified access token into a
 * {@link CallerIdentity}; every other class only receives the result.
 * <p>
 * Spring Security has already checked the token (SecurityConfig: signature with user-service's public key, expiry,
 * issuer, audience). Here: the subject must be a user id and the "role" claim one of the platform's roles, and the
 * token must still be good for a whole answer (ai-mcp-server calls the platform with it for up to 150 s). Nothing in
 * the request body or the chat message can change who the caller is.
 * <p>
 * Only the numeric user id is logged, never the token.
 */
@Slf4j
@Component
public class CallerIdentityResolver {

    static final String ROLE_CLAIM = "role";

    private final Clock clock;
    private final Duration minTokenValidity;

    public CallerIdentityResolver(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.minTokenValidity = properties.identity().minTokenValidity();
    }

    /**
     * @param jwt the verified token of the request, or null when the request carried none
     * @throws AuthenticationFailedException 401: no token, a token without a valid user or role, or one about to
     *                                       expire (the browser renews it and asks again)
     */
    public CallerIdentity resolve(Jwt jwt) {
        if (jwt == null) {
            throw new AuthenticationFailedException("Sign in to use the assistant.");
        }
        long userId = parseUserId(jwt.getSubject());
        Role role = parseRole(jwt.getClaimAsString(ROLE_CLAIM));
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(clock.instant().plus(minTokenValidity))) {
            log.debug("Access token of user {} expires too soon for an answer", userId);
            throw new AuthenticationFailedException("Your session needs renewing. Please try again.");
        }
        MDC.put(RequestIdFilter.MDC_USER_ID, String.valueOf(userId));
        return new CallerIdentity(userId, role, jwt.getTokenValue());
    }

    private static long parseUserId(String subject) {
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new NumberFormatException();
            }
            return userId;
        } catch (NumberFormatException e) {
            log.warn("Access token refused: its subject is not a user id");
            throw new AuthenticationFailedException("The user could not be identified.");
        }
    }

    private static Role parseRole(String claim) {
        if (claim == null) {
            throw new AuthenticationFailedException("The user could not be identified.");
        }
        try {
            return Role.valueOf(claim.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Access token refused: unknown role");
            throw new AuthenticationFailedException("The user could not be identified.");
        }
    }
}
