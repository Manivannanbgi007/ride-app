package com.fooddelivery.userservice.web;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.entity.Role;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh-token cookies. Each area of the web application has its own cookie, so a customer, a restaurant owner,
 * a delivery partner and an admin can be signed in at the same time in one browser:
 * {@code fd_refresh_customer}, {@code fd_refresh_owner}, {@code fd_refresh_partner}, {@code fd_refresh_admin}.
 * <p>
 * The cookies are HttpOnly (JavaScript cannot read them, so an XSS bug cannot steal them), SameSite=Strict (never sent
 * from another site's page) and limited to the session endpoints by their path. They have no expiry date: they live
 * until the browser closes, while the server decides how long the session itself is valid.
 */
@Component
public class RefreshCookies {

    private final AppProperties.RefreshCookie settings;

    public RefreshCookies(AppProperties appProperties) {
        this.settings = appProperties.auth().refreshCookie();
    }

    public static String nameFor(Role role) {
        return "fd_refresh_" + switch (role) {
            case CUSTOMER -> "customer";
            case RESTAURANT_ADMIN -> "owner";
            case DELIVERY_PARTNER -> "partner";
            case PLATFORM_ADMIN -> "admin";
        };
    }

    public Optional<String> read(HttpServletRequest request, Role role) {
        return Optional.ofNullable(readAll(request).get(role));
    }

    /** Every refresh cookie the browser sent, by area. */
    public Map<Role, String> readAll(HttpServletRequest request) {
        Map<Role, String> tokens = new EnumMap<>(Role.class);
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return tokens;
        }
        for (Role role : Role.values()) {
            String name = nameFor(role);
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    tokens.put(role, cookie.getValue());
                }
            }
        }
        return tokens;
    }

    public ResponseCookie create(Role role, String refreshToken) {
        return base(role, refreshToken).build();
    }

    /** Deletes the cookie (same name, path and flags, expired). */
    public ResponseCookie clear(Role role) {
        return base(role, "").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(Role role, String value) {
        return ResponseCookie.from(nameFor(role), value)
                .httpOnly(true)
                .secure(settings.secure())
                .sameSite(settings.sameSite())
                .path(settings.path());
    }
}
