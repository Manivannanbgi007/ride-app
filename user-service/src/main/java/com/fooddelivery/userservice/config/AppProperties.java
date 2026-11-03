package com.fooddelivery.userservice.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone time zone of the business: statistics count calendar days in it
 * @param bootstrapAdmin first platform administrator, created at startup if configured
 * @param addresses      address book limits
 * @param jwt            access tokens (signed JWTs) issued at sign-in
 * @param auth           sign-in sessions: refresh tokens and their cookie
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue BootstrapAdmin bootstrapAdmin,
                            @DefaultValue Addresses addresses,
                            @DefaultValue Jwt jwt,
                            @DefaultValue Auth auth) {

    public record BootstrapAdmin(String email,
                                 String password,
                                 @DefaultValue("Platform Administrator") String fullName) {

        @Override
        public String toString() {
            return "BootstrapAdmin[email=" + email + ", password=****, fullName=" + fullName + "]";
        }
    }

    public record Addresses(@DefaultValue("10") int maxPerUser) {
    }

    /**
     * Access tokens.
     *
     * @param issuer            the "iss" claim; every service checks it (a fixed name, not a host name)
     * @param audience          the "aud" claim; every service checks it
     * @param accessTokenTtl    how long an access token is valid
     * @param privateKey        RSA private key that signs the tokens (base64 PKCS#8, from JWT_PRIVATE_KEY)
     * @param allowEphemeralKey development only: without a private key, generate a temporary one at startup
     */
    public record Jwt(@DefaultValue("https://auth.fooddelivery.local") String issuer,
                      @DefaultValue("food-delivery-api") String audience,
                      @DefaultValue("15m") Duration accessTokenTtl,
                      String privateKey,
                      @DefaultValue("false") boolean allowEphemeralKey) {

        // Records print every field: never put the key into a log line by accident
        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", audience=" + audience + ", accessTokenTtl=" + accessTokenTtl
                    + ", privateKey=" + (privateKey == null || privateKey.isBlank() ? "<none>" : "****")
                    + ", allowEphemeralKey=" + allowEphemeralKey + "]";
        }
    }

    /**
     * Sign-in sessions.
     *
     * @param refreshToken   refresh token lifetime and rotation
     * @param refreshCookie  the HttpOnly cookie that carries the refresh token
     * @param allowedOrigins web pages allowed to call login, refresh and logout (checked against the Origin header)
     */
    public record Auth(@DefaultValue RefreshToken refreshToken,
                       @DefaultValue RefreshCookie refreshCookie,
                       @DefaultValue("http://localhost:5180") List<String> allowedOrigins) {
    }

    /**
     * @param ttl         a refresh token expires this long after it was issued (every refresh issues a new one)
     * @param reuseGrace  an already exchanged token presented again within this time is a parallel refresh (two tabs),
     *                    not a theft: answered 409 without ending the session
     * @param cleanupCron when expired tokens are deleted
     */
    public record RefreshToken(@DefaultValue("7d") Duration ttl,
                               @DefaultValue("10s") Duration reuseGrace,
                               @DefaultValue("0 30 3 * * *") String cleanupCron) {
    }

    /**
     * @param secure   send only over HTTPS (switched off for plain http://localhost in the dev profile)
     * @param sameSite Strict: the browser never sends the cookie from another site's page (CSRF protection)
     * @param path     the cookie reaches only the session endpoints, never the other APIs
     */
    public record RefreshCookie(@DefaultValue("true") boolean secure,
                                @DefaultValue("Strict") String sameSite,
                                @DefaultValue("/api/v1/auth") String path) {
    }
}
