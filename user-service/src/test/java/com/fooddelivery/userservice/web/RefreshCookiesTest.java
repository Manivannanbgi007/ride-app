package com.fooddelivery.userservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.exception.ForbiddenException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshCookiesTest {

    private static final AppProperties PROPERTIES = new AppProperties("Asia/Kolkata", null, null, null,
            new AppProperties.Auth(null, new AppProperties.RefreshCookie(true, "Strict", "/api/v1/auth"),
                    List.of("http://localhost:5180", "https://app.example.com/")));

    private final RefreshCookies refreshCookies = new RefreshCookies(PROPERTIES);

    @Test
    void eachAreaHasItsOwnCookie() {
        assertThat(RefreshCookies.nameFor(Role.CUSTOMER)).isEqualTo("fd_refresh_customer");
        assertThat(RefreshCookies.nameFor(Role.RESTAURANT_ADMIN)).isEqualTo("fd_refresh_owner");
        assertThat(RefreshCookies.nameFor(Role.DELIVERY_PARTNER)).isEqualTo("fd_refresh_partner");
        assertThat(RefreshCookies.nameFor(Role.PLATFORM_ADMIN)).isEqualTo("fd_refresh_admin");
    }

    @Test
    void theCookieIsHttpOnlySecureStrictAndLimitedToTheSessionEndpoints() {
        String header = refreshCookies.create(Role.RESTAURANT_ADMIN, "token-123").toString();

        assertThat(header)
                .startsWith("fd_refresh_owner=token-123")
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict")
                // A session cookie: gone when the browser closes
                .doesNotContain("Max-Age", "Expires");
    }

    @Test
    void clearingExpiresTheSameCookie() {
        String header = refreshCookies.clear(Role.CUSTOMER).toString();

        assertThat(header).startsWith("fd_refresh_customer=;").contains("Max-Age=0", "Path=/api/v1/auth", "HttpOnly");
    }

    @Test
    void readsTheCookiesByArea() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("fd_refresh_owner", "owner-token"), new Cookie("other", "x"),
                new Cookie("fd_refresh_admin", ""));

        assertThat(refreshCookies.readAll(request)).isEqualTo(Map.of(Role.RESTAURANT_ADMIN, "owner-token"));
        assertThat(refreshCookies.read(request, Role.RESTAURANT_ADMIN)).contains("owner-token");
        assertThat(refreshCookies.read(request, Role.CUSTOMER)).isEmpty();
        assertThat(refreshCookies.readAll(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void onlyOurWebPagesMayUseTheSessionEndpoints() {
        AllowedOrigins allowedOrigins = new AllowedOrigins(PROPERTIES);

        MockHttpServletRequest fromApp = new MockHttpServletRequest();
        fromApp.addHeader("Origin", "http://localhost:5180");
        MockHttpServletRequest fromSecondApp = new MockHttpServletRequest();
        fromSecondApp.addHeader("Origin", "https://app.example.com");
        MockHttpServletRequest fromAProgram = new MockHttpServletRequest();
        MockHttpServletRequest fromAnotherSite = new MockHttpServletRequest();
        fromAnotherSite.addHeader("Origin", "https://evil.example");

        allowedOrigins.check(fromApp);
        allowedOrigins.check(fromSecondApp);
        allowedOrigins.check(fromAProgram);
        assertThatThrownBy(() -> allowedOrigins.check(fromAnotherSite)).isInstanceOf(ForbiddenException.class);
    }
}
