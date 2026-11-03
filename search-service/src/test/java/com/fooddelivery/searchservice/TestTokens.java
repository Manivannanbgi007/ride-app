package com.fooddelivery.searchservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Who calls in a MockMvc test: the user a verified access token would name (Spring Security's test support puts the
 * token straight into the security context, so nothing is signed here; the checks of real signed tokens are tested
 * separately), or another service with the internal APIs' shared token.
 */
public final class TestTokens {

    /** The shared secret of the internal APIs in the tests (application-test.yml) */
    public static final String SERVICE_TOKEN = "test-internal-service-token";

    private TestTokens() {
    }

    /** The access token of this user with this role ("CUSTOMER", "PLATFORM_ADMIN"...). */
    public static RequestPostProcessor as(Object userId, String role) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** A call of another service into an internal API. */
    public static RequestPostProcessor serviceToken() {
        return request -> {
            request.addHeader("X-Service-Token", SERVICE_TOKEN);
            return request;
        };
    }
}
