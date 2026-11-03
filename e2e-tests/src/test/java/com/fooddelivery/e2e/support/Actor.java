package com.fooddelivery.e2e.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Who calls: a person signed in through the API Gateway, as the web application signs in. Every call sends their
 * access token ({@code Authorization: Bearer}); the token lives 15 minutes, so a longer flow signs in again shortly
 * before it expires.
 */
public final class Actor {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String RESTAURANT_ADMIN = "RESTAURANT_ADMIN";
    public static final String DELIVERY_PARTNER = "DELIVERY_PARTNER";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /** A token with less time left than this is not sent: the person signs in again first */
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(1);

    private final long userId;
    private final String role;
    private final String email;
    private final String password;
    private String accessToken;
    private Instant expiresAt;

    private Actor(long userId, String role, String email, String password) {
        this.userId = userId;
        this.role = role;
        this.email = email;
        this.password = password;
    }

    /** Signs in to the area of {@code role} (another kind of account is refused) and keeps the access token. */
    public static Actor signIn(String email, String password, String role) {
        Response login = login(email, password, role);
        Actor actor = new Actor(login.number("/userId"), role, email, password);
        actor.keep(login);
        return actor;
    }

    private static Response login(String email, String password, String role) {
        return Api.anonymous().post("/api/v1/auth/login", Map.of("email", email, "password", password, "role", role))
                .expect(200);
    }

    private void keep(Response login) {
        accessToken = login.text("/accessToken");
        expiresAt = Instant.now().plusSeconds(login.number("/expiresIn"));
    }

    /** The access token to send now (signed in again when it is about to expire). */
    synchronized String accessToken() {
        if (Instant.now().plus(RENEW_BEFORE).isAfter(expiresAt)) {
            keep(login(email, password, role));
        }
        return accessToken;
    }

    public long userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    // Never the token or the password in a test report
    @Override
    public String toString() {
        return "Actor[userId=" + userId + ", role=" + role + "]";
    }
}
