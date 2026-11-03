package com.fooddelivery.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.userservice.dto.request.LoginRequest;
import com.fooddelivery.userservice.dto.request.RegisterRequest;
import com.fooddelivery.userservice.dto.response.UserResponse;
import com.fooddelivery.userservice.entity.RevokeReason;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.exception.AuthenticationFailedException;
import com.fooddelivery.userservice.exception.BusinessRuleException;
import com.fooddelivery.userservice.exception.EmailAlreadyRegisteredException;
import com.fooddelivery.userservice.exception.ForbiddenException;
import com.fooddelivery.userservice.exception.WrongAccountTypeException;
import com.fooddelivery.userservice.kafka.producer.UserEventPublisher;
import com.fooddelivery.userservice.mapper.UserMapper;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.service.AccessTokenService.AccessToken;
import com.fooddelivery.userservice.service.AuthService.AuthSession;
import com.fooddelivery.userservice.service.RefreshTokenService.IssuedRefreshToken;
import com.fooddelivery.userservice.service.RefreshTokenService.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final AccessToken ACCESS_TOKEN = new AccessToken("signed.jwt.value", NOW.plusSeconds(900), 900);
    private static final IssuedRefreshToken REFRESH_TOKEN = new IssuedRefreshToken("refresh-1", NOW.plusSeconds(3600));

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserEventPublisher userEventPublisher;

    @Mock
    private AccessTokenService accessTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("timing-protection-dummy-password")).thenReturn("dummy-hash");
        authService = new AuthService(userRepository, passwordEncoder, new UserMapper(), userEventPublisher,
                accessTokenService, refreshTokenService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerCreatesActiveCustomerWithNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("priya@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Priya1234")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        UserResponse response = authService.register(
                new RegisterRequest("  Priya@Example.COM ", "Priya1234", " Priya Sharma ", null, null));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.email()).isEqualTo("priya@example.com");
        assertThat(response.fullName()).isEqualTo("Priya Sharma");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        verify(userEventPublisher).userRegistered(savedUser.getValue());
    }

    @Test
    void registerRejectsPlatformAdminRole() {
        RegisterRequest request = new RegisterRequest("x@example.com", "Secret123", "X", null, Role.PLATFORM_ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
        verify(userEventPublisher, never()).userRegistered(any());
    }

    @Test
    void registerRejectsAlreadyRegisteredEmail() {
        when(userRepository.existsByEmail("priya@example.com")).thenReturn(true);

        var registerRequest = new RegisterRequest("Priya@example.com", "Priya1234", "Priya", null, null);
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginStartsASessionAndRecordsLastLoginTime() {
        User user = givenActiveCustomerWithCorrectPassword();
        when(refreshTokenService.startSession(user)).thenReturn(REFRESH_TOKEN);
        when(accessTokenService.issue(user)).thenReturn(ACCESS_TOKEN);

        AuthSession session = authService.login(new LoginRequest("priya@example.com", "Priya1234"), Map.of());

        assertThat(session.body().userId()).isEqualTo(1L);
        assertThat(session.body().lastLoginAt()).isEqualTo(NOW);
        assertThat(session.body().accessToken()).isEqualTo("signed.jwt.value");
        assertThat(session.body().tokenType()).isEqualTo("Bearer");
        assertThat(session.body().expiresIn()).isEqualTo(900);
        assertThat(session.role()).isEqualTo(Role.CUSTOMER);
        assertThat(session.refreshToken()).isEqualTo("refresh-1");
        verify(userRepository).updateLastLoginAt(1L, NOW);
    }

    @Test
    void loginToTheRightAreaSucceeds() {
        User user = givenActiveCustomerWithCorrectPassword();
        when(refreshTokenService.startSession(user)).thenReturn(REFRESH_TOKEN);
        when(accessTokenService.issue(user)).thenReturn(ACCESS_TOKEN);

        AuthSession session = authService.login(
                new LoginRequest("priya@example.com", "Priya1234", Role.CUSTOMER), Map.of());

        assertThat(session.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void loginToAnotherAreaIsRefusedBeforeAnySessionStarts() {
        givenActiveCustomerWithCorrectPassword();

        var ownerPageLogin = new LoginRequest("priya@example.com", "Priya1234", Role.RESTAURANT_ADMIN);
        assertThatThrownBy(() -> authService.login(ownerPageLogin, Map.of(Role.RESTAURANT_ADMIN, "owner-cookie")))
                .isInstanceOf(WrongAccountTypeException.class);
        // The owner session this browser may have stays untouched, and no new session or token is created
        verifyNoInteractions(refreshTokenService, accessTokenService);
        verify(userRepository, never()).updateLastLoginAt(anyLong(), any());
    }

    @Test
    void loginEndsTheSessionItReplaces() {
        User user = givenActiveCustomerWithCorrectPassword();
        when(refreshTokenService.startSession(user)).thenReturn(REFRESH_TOKEN);
        when(accessTokenService.issue(user)).thenReturn(ACCESS_TOKEN);

        authService.login(new LoginRequest("priya@example.com", "Priya1234"),
                Map.of(Role.CUSTOMER, "old-customer-cookie", Role.RESTAURANT_ADMIN, "owner-cookie"));

        // Only the same area's previous session is ended; the owner area keeps its own session
        verify(refreshTokenService).endSession("old-customer-cookie", RevokeReason.REPLACED);
        verify(refreshTokenService, never()).endSession("owner-cookie", RevokeReason.REPLACED);
    }

    @Test
    void loginWithWrongPasswordFails() {
        when(userRepository.findByEmail("priya@example.com")).thenReturn(Optional.of(user(1L, UserStatus.ACTIVE)));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        var loginRequest = new LoginRequest("priya@example.com", "wrong");
        assertThatThrownBy(() -> authService.login(loginRequest, Map.of()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
        verify(userRepository, never()).updateLastLoginAt(anyLong(), any());
        verifyNoInteractions(refreshTokenService, accessTokenService);
    }

    @Test
    void loginWithUnknownEmailFailsWithSameMessageAndStillChecksAPassword() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        var loginRequest = new LoginRequest("nobody@example.com", "whatever1");
        assertThatThrownBy(() -> authService.login(loginRequest, Map.of()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
        // Timing protection: a password check happens even though the user does not exist.
        verify(passwordEncoder).matches("whatever1", "dummy-hash");
    }

    @Test
    void deactivatedUserCannotLogIn() {
        when(userRepository.findByEmail("priya@example.com")).thenReturn(Optional.of(user(1L, UserStatus.INACTIVE)));
        when(passwordEncoder.matches("Priya1234", "stored-hash")).thenReturn(true);

        var loginRequest = new LoginRequest("priya@example.com", "Priya1234");
        assertThatThrownBy(() -> authService.login(loginRequest, Map.of()))
                .isInstanceOf(ForbiddenException.class)
                .isNotInstanceOf(WrongAccountTypeException.class);
        verify(userRepository, never()).updateLastLoginAt(anyLong(), any());
        verifyNoInteractions(refreshTokenService, accessTokenService);
    }

    @Test
    void refreshReturnsANewAccessTokenAndTheRotatedRefreshToken() {
        User user = user(1L, UserStatus.ACTIVE);
        user.setLastLoginAt(NOW.minusSeconds(600));
        when(refreshTokenService.rotate("refresh-old", Role.CUSTOMER)).thenReturn(new Rotation(user, REFRESH_TOKEN));
        when(accessTokenService.issue(user)).thenReturn(ACCESS_TOKEN);

        AuthSession session = authService.refresh(Role.CUSTOMER, "refresh-old");

        assertThat(session.refreshToken()).isEqualTo("refresh-1");
        assertThat(session.body().accessToken()).isEqualTo("signed.jwt.value");
        // A refresh is not a new sign-in
        assertThat(session.body().lastLoginAt()).isEqualTo(NOW.minusSeconds(600));
        verify(userRepository, never()).updateLastLoginAt(anyLong(), any());
    }

    @Test
    void logoutEndsTheSession() {
        authService.logout("refresh-1");

        verify(refreshTokenService).endSession("refresh-1", RevokeReason.LOGOUT);
    }

    @Test
    void sessionToStringHidesTheRefreshToken() {
        AuthSession session = new AuthSession(
                new UserMapper().toLoginResponse(user(1L, UserStatus.ACTIVE), NOW, "secret.jwt", 900),
                Role.CUSTOMER, "secret-refresh");

        assertThat(session.toString()).doesNotContain("secret");
    }

    private User givenActiveCustomerWithCorrectPassword() {
        User user = user(1L, UserStatus.ACTIVE);
        when(userRepository.findByEmail("priya@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Priya1234", "stored-hash")).thenReturn(true);
        return user;
    }

    private static User user(Long id, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setEmail("priya@example.com");
        user.setPasswordHash("stored-hash");
        user.setFullName("Priya Sharma");
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        return user;
    }

    private static User withId(User user, Long id) {
        user.setId(id);
        return user;
    }
}
