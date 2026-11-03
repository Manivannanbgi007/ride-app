package com.fooddelivery.userservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.userservice.dto.request.LoginRequest;
import com.fooddelivery.userservice.dto.request.RegisterRequest;
import com.fooddelivery.userservice.dto.response.LoginResponse;
import com.fooddelivery.userservice.dto.response.UserResponse;
import com.fooddelivery.userservice.entity.RevokeReason;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.exception.AuthenticationFailedException;
import com.fooddelivery.userservice.exception.BusinessRuleException;
import com.fooddelivery.userservice.exception.ConflictException;
import com.fooddelivery.userservice.exception.EmailAlreadyRegisteredException;
import com.fooddelivery.userservice.exception.ForbiddenException;
import com.fooddelivery.userservice.exception.WrongAccountTypeException;
import com.fooddelivery.userservice.kafka.producer.UserEventPublisher;
import com.fooddelivery.userservice.mapper.UserMapper;
import com.fooddelivery.userservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

    /** Roles people can choose when signing up. PLATFORM_ADMIN is created only by bootstrap or by another admin. */
    private static final Set<Role> SELF_REGISTRATION_ROLES =
            EnumSet.of(Role.CUSTOMER, Role.RESTAURANT_ADMIN, Role.DELIVERY_PARTNER);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserEventPublisher userEventPublisher;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    // Checked when the email does not exist, so "unknown email" takes as long as "wrong password"
    // and response times do not reveal which emails are registered.
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper,
                       UserEventPublisher userEventPublisher,
                       AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.userEventPublisher = userEventPublisher;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("timing-protection-dummy-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        Role role = request.role() == null ? Role.CUSTOMER : request.role();
        if (!SELF_REGISTRATION_ROLES.contains(role)) {
            throw new BusinessRuleException("Role " + role + " cannot be chosen at registration");
        }

        String email = UserInputNormalizer.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        String phone = UserInputNormalizer.normalizePhone(request.phone());
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ConflictException("Phone number is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);
        log.info("User registered. userId={} role={}", savedUser.getId(), savedUser.getRole());

        // Sent to Kafka after commit: other services (e.g. notifications) react to new accounts.
        userEventPublisher.userRegistered(savedUser);

        return userMapper.toUserResponse(savedUser);
    }

    /**
     * Signs a user in: checks the password, then starts a session (refresh token for the cookie) and issues an access
     * token.
     *
     * @param request              email, password and optionally the area (role) the user signs in to
     * @param previousRefreshToken the refresh cookie this browser already has for that area, if any: that older
     *                             session is ended, because the new sign-in replaces it
     */
    @Transactional
    public AuthSession login(LoginRequest request, Map<Role, String> previousRefreshToken) {
        Optional<User> foundUser = userRepository.findByEmail(UserInputNormalizer.normalizeEmail(request.email()));

        String passwordHash = foundUser.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

        // Same message for "unknown email" and "wrong password": do not reveal which emails exist.
        if (foundUser.isEmpty() || !passwordMatches) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        User user = foundUser.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is deactivated. Contact support.");
        }
        // Checked on the server, before any cookie is set: a customer signing in on the owner page must not replace
        // the owner session this browser may already have.
        if (request.role() != null && request.role() != user.getRole()) {
            throw new WrongAccountTypeException();
        }

        String previous = previousRefreshToken.get(user.getRole());
        if (previous != null) {
            refreshTokenService.endSession(previous, RevokeReason.REPLACED);
        }

        Instant loginTime = clock.instant();
        userRepository.updateLastLoginAt(user.getId(), loginTime);
        log.info("User logged in. userId={} role={}", user.getId(), user.getRole());

        return session(user, loginTime, refreshTokenService.startSession(user));
    }

    /**
     * Exchanges the area's refresh token for a new one and a fresh access token.
     *
     * @throws com.fooddelivery.userservice.exception.SessionEndedException    the user has to sign in again (401)
     * @throws com.fooddelivery.userservice.exception.RefreshConflictException another tab refreshed a moment ago (409)
     */
    public AuthSession refresh(Role area, String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken, area);
        return session(rotation.user(), rotation.user().getLastLoginAt(), rotation.refreshToken());
    }

    /** Signs the area's session out. Already ended or unknown sessions are fine: signing out twice is not an error. */
    public void logout(String refreshToken) {
        refreshTokenService.endSession(refreshToken, RevokeReason.LOGOUT);
    }

    private AuthSession session(User user, Instant lastLoginAt, RefreshTokenService.IssuedRefreshToken refreshToken) {
        AccessTokenService.AccessToken accessToken = accessTokenService.issue(user);
        LoginResponse body = userMapper.toLoginResponse(
                user, lastLoginAt, accessToken.value(), accessToken.expiresInSeconds());
        return new AuthSession(body, user.getRole(), refreshToken.value());
    }

    /**
     * What the controller needs after a sign-in or refresh: the response body, and the refresh token for the area's
     * HttpOnly cookie (it never appears in a response body).
     */
    public record AuthSession(LoginResponse body, Role role, String refreshToken) {

        @Override
        public String toString() {
            return "AuthSession[userId=" + body.userId() + ", role=" + role + ", refreshToken=****]";
        }
    }
}
