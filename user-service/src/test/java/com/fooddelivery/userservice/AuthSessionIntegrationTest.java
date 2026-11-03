package com.fooddelivery.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Sign-in sessions end to end against a real MySQL: tokens, the refresh cookie, rotation, sign-out and the account
 * changes that end sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthSessionIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "AdminPass123";
    private static final String WEB_APP = "http://localhost:5180";
    private static final String CUSTOMER_COOKIE = "fd_refresh_customer";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signInGivesAnAccessTokenThatThePublishedKeyVerifiesAndSetsTheAreaCookie() throws Exception {
        String email = registerCustomer();

        MvcResult result = mockMvc.perform(login(email, "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith(CUSTOMER_COOKIE + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Secure");
        // The refresh token is only in the cookie, never in the body
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(cookieValue(result));

        // Any service can check the token with the public key published at the JWKS endpoint
        String jwks = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Jwt jwt = decoder(jwks).decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(((Number) JsonPath.read(body, "$.userId")).longValue()));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void refreshExchangesTheCookieForANewOneAndAParallelRefreshIsToldToRetry() throws Exception {
        String first = cookieValue(mockMvc.perform(login(registerCustomer(), null)).andReturn());

        MvcResult refreshed = mockMvc.perform(refresh(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String second = cookieValue(refreshed);
        assertThat(second).isNotEqualTo(first);

        // The same old cookie a moment later (a second tab): 409, and the browser's new cookie is left alone
        mockMvc.perform(refresh(first))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Set-Cookie"));

        mockMvc.perform(refresh(second)).andExpect(status().isOk());
    }

    @Test
    void signOutEndsTheSessionAndRemovesTheCookie() throws Exception {
        String cookie = cookieValue(mockMvc.perform(login(registerCustomer(), null)).andReturn());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Origin", WEB_APP)
                        .cookie(new Cookie(CUSTOMER_COOKIE, cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"CUSTOMER\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        mockMvc.perform(refresh(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your session has ended. Please sign in again."))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void signingInAgainEndsThePreviousSessionOfThatArea() throws Exception {
        String email = registerCustomer();
        String old = cookieValue(mockMvc.perform(login(email, null)).andReturn());

        mockMvc.perform(login(email, null).cookie(new Cookie(CUSTOMER_COOKIE, old))).andExpect(status().isOk());

        mockMvc.perform(refresh(old)).andExpect(status().isUnauthorized());
    }

    @Test
    void signingInToTheWrongAreaIsRefusedWithoutTouchingCookies() throws Exception {
        String email = registerCustomer();

        mockMvc.perform(login(email, "RESTAURANT_ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_ACCOUNT_TYPE"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void deactivationAndPasswordChangeEndTheSessions() throws Exception {
        long adminId = userIdOf(mockMvc.perform(login(ADMIN_EMAIL, ADMIN_PASSWORD, null)).andReturn());

        String deactivatedEmail = registerCustomer();
        MvcResult deactivatedLogin = mockMvc.perform(login(deactivatedEmail, null)).andReturn();
        mockMvc.perform(put("/api/v1/admin/users/{userId}/deactivate", userIdOf(deactivatedLogin))
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Fraud suspected\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(refresh(cookieValue(deactivatedLogin))).andExpect(status().isUnauthorized());

        MvcResult passwordLogin = mockMvc.perform(login(registerCustomer(), null)).andReturn();
        mockMvc.perform(put("/api/v1/users/me/password")
                        // The access token of that sign-in, checked by user-service with its own key
                        .header("Authorization", "Bearer " + accessTokenOf(passwordLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"Secret1234\", \"newPassword\": \"Changed1234\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(refresh(cookieValue(passwordLogin))).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherWebsiteCannotUseTheSessionEndpoints() throws Exception {
        String email = registerCustomer();
        String cookie = cookieValue(mockMvc.perform(login(email, null)).andReturn());

        mockMvc.perform(login(email, "Secret1234", null, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(refresh(cookie, "https://evil.example"))
                .andExpect(status().isForbidden());
        // The session itself was not touched
        mockMvc.perform(refresh(cookie)).andExpect(status().isOk());
    }

    @Test
    void refreshNeedsTheAreasCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"CUSTOMER\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String registerCustomer() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Secret1234", "fullName": "Session Tester"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private static MockHttpServletRequestBuilder login(String email, String role) {
        return login(email, "Secret1234", role);
    }

    private static MockHttpServletRequestBuilder login(String email, String password, String role) {
        return login(email, password, role, WEB_APP);
    }

    /** A sign-in sent by the web page at {@code origin} (browsers always send the Origin header with a POST). */
    private static MockHttpServletRequestBuilder login(String email, String password, String role, String origin) {
        String roleField = role == null ? "" : ", \"role\": \"%s\"".formatted(role);
        return post("/api/v1/auth/login")
                .header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"%s}".formatted(email, password, roleField));
    }

    private static MockHttpServletRequestBuilder refresh(String cookie) {
        return refresh(cookie, WEB_APP);
    }

    private static MockHttpServletRequestBuilder refresh(String cookie, String origin) {
        return post("/api/v1/auth/refresh")
                .header("Origin", origin)
                .cookie(new Cookie(CUSTOMER_COOKIE, cookie))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"CUSTOMER\"}");
    }

    private static String cookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).as("Set-Cookie").isNotNull();
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private static String accessTokenOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static long userIdOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.userId")).longValue();
    }

    /** What a service does with the JWKS: check signature, expiry, issuer and audience. */
    private static NimbusJwtDecoder decoder(String jwks) throws Exception {
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeys().getFirst();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://auth.fooddelivery.local"),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("food-delivery-api"))));
        return decoder;
    }
}
