package com.fooddelivery.userservice.controller;

import java.util.Optional;

import com.fooddelivery.userservice.dto.request.LoginRequest;
import com.fooddelivery.userservice.dto.request.RegisterRequest;
import com.fooddelivery.userservice.dto.request.SessionRequest;
import com.fooddelivery.userservice.dto.response.LoginResponse;
import com.fooddelivery.userservice.dto.response.UserResponse;
import com.fooddelivery.userservice.exception.SessionEndedException;
import com.fooddelivery.userservice.service.AuthService;
import com.fooddelivery.userservice.service.AuthService.AuthSession;
import com.fooddelivery.userservice.web.AllowedOrigins;
import com.fooddelivery.userservice.web.RefreshCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and sign-in sessions.
 * <p>
 * A session has two tokens: a short-lived access token in the response body (sent as {@code Authorization: Bearer}),
 * and a long-lived refresh token in an HttpOnly cookie of the signed-in area (used only by refresh and logout).
 */
@Tag(name = "Auth", description = "Registration, sign-in, session refresh and sign-out")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies refreshCookies;
    private final AllowedOrigins allowedOrigins;

    @Operation(summary = "Register a new account (customer by default)")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Sign in with email and password",
            description = "Returns an access token and sets the area's HttpOnly refresh cookie. Send \"role\" to sign in "
                    + "to one area only: another kind of account gets 403 with code WRONG_ACCOUNT_TYPE and no cookie.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        allowedOrigins.check(http);
        AuthSession session = authService.login(request, refreshCookies.readAll(http));
        return withSession(session);
    }

    @Operation(summary = "Get a new access token with the area's refresh cookie",
            description = "401 (cookie cleared): sign in again. 409: another tab refreshed a moment ago; try again.")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody SessionRequest request,
                                                 HttpServletRequest http,
                                                 HttpServletResponse response) {
        allowedOrigins.check(http);
        Optional<String> refreshToken = refreshCookies.read(http, request.role());
        if (refreshToken.isEmpty()) {
            throw new SessionEndedException();
        }
        try {
            return withSession(authService.refresh(request.role(), refreshToken.get()));
        } catch (SessionEndedException e) {
            // The cookie is useless now: remove it from the browser too (the error body is written afterwards)
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.clear(request.role()).toString());
            throw e;
        }
    }

    @Operation(summary = "Sign out of one area", description = "Ends the area's session and removes its cookie.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody SessionRequest request, HttpServletRequest http) {
        allowedOrigins.check(http);
        refreshCookies.read(http, request.role()).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear(request.role()).toString())
                .build();
    }

    private ResponseEntity<LoginResponse> withSession(AuthSession session) {
        return ResponseEntity.ok()
                // Tokens must never be stored by a browser or proxy cache
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookies.create(session.role(), session.refreshToken()).toString())
                .body(session.body());
    }
}
