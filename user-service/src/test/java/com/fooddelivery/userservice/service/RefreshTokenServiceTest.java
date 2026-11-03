package com.fooddelivery.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.entity.RefreshToken;
import com.fooddelivery.userservice.entity.RevokeReason;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.exception.RefreshConflictException;
import com.fooddelivery.userservice.exception.SessionEndedException;
import com.fooddelivery.userservice.repository.RefreshTokenRepository;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.service.RefreshTokenService.IssuedRefreshToken;
import com.fooddelivery.userservice.service.RefreshTokenService.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration GRACE = Duration.ofSeconds(10);
    private static final String PRESENTED = "presented-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        AppProperties.Auth auth = new AppProperties.Auth(
                new AppProperties.RefreshToken(TTL, GRACE, "0 30 3 * * *"),
                new AppProperties.RefreshCookie(true, "Strict", "/api/v1/auth"),
                List.of("http://localhost:5180"));
        AppProperties properties = new AppProperties("Asia/Kolkata", null, null, null, auth);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aNewSessionStoresOnlyTheHashOfARandomToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IssuedRefreshToken first = refreshTokenService.startSession(customer(UserStatus.ACTIVE));
        IssuedRefreshToken second = refreshTokenService.startSession(customer(UserStatus.ACTIVE));

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(saved.capture());
        RefreshToken stored = saved.getAllValues().getFirst();
        assertThat(stored.getTokenHash())
                .isEqualTo(RefreshTokenService.hash(first.value()))
                .isNotEqualTo(first.value())
                .hasSize(64);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(stored.getRole()).isEqualTo(Role.CUSTOMER);
        // 256 random bits, url-safe; each sign-in is its own family
        assertThat(first.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(saved.getAllValues().get(1).getFamilyId()).isNotEqualTo(stored.getFamilyId());
    }

    @Test
    void rotationReplacesTheTokenWithANewOneInTheSameFamily() {
        RefreshToken current = givenStoredToken(Role.CUSTOMER, NOW.plus(Duration.ofDays(3)));
        User user = customer(UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 99L));

        Rotation rotation = refreshTokenService.rotate(PRESENTED, Role.CUSTOMER);

        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.refreshToken().value()).isNotEqualTo(PRESENTED);
        assertThat(rotation.refreshToken().expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(current.getUsedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedById()).isEqualTo(99L);
        ArgumentCaptor<RefreshToken> successor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(successor.capture());
        assertThat(successor.getValue().getFamilyId()).isEqualTo("family-1");
    }

    @Test
    void anUnknownTokenEndsTheSession() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.CUSTOMER))
                .isInstanceOf(SessionEndedException.class);
    }

    @Test
    void anExpiredTokenEndsTheSession() {
        givenStoredToken(Role.CUSTOMER, NOW);

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.CUSTOMER))
                .isInstanceOf(SessionEndedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void aRevokedTokenEndsTheSession() {
        RefreshToken token = givenStoredToken(Role.CUSTOMER, NOW.plus(TTL));
        set(token, "revokedAt", NOW.minusSeconds(5));

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.CUSTOMER))
                .isInstanceOf(SessionEndedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void anotherAreasTokenIsNotAccepted() {
        givenStoredToken(Role.CUSTOMER, NOW.plus(TTL));

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.PLATFORM_ADMIN))
                .isInstanceOf(SessionEndedException.class);
    }

    @Test
    void aTokenUsedAMomentAgoIsAParallelRefreshNotATheft() {
        RefreshToken token = givenStoredToken(Role.CUSTOMER, NOW.plus(TTL));
        token.markUsed(NOW.minusSeconds(3), 8L);

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.CUSTOMER))
                .isInstanceOf(RefreshConflictException.class);
        verify(refreshTokenRepository, never()).revokeFamily(anyString(), any(), any());
    }

    @Test
    void aTokenUsedAgainLaterIsStolenSoTheWholeSessionIsRevoked() {
        RefreshToken token = givenStoredToken(Role.CUSTOMER, NOW.plus(TTL));
        token.markUsed(NOW.minusSeconds(60), 8L);

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.CUSTOMER))
                .isInstanceOf(SessionEndedException.class);
        verify(refreshTokenRepository).revokeFamily("family-1", RevokeReason.REUSE_DETECTED, NOW);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void aDeactivatedUsersSessionEnds() {
        givenStoredToken(Role.CUSTOMER, NOW.plus(TTL));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer(UserStatus.INACTIVE)));

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.CUSTOMER))
                .isInstanceOf(SessionEndedException.class);
        verify(refreshTokenRepository).revokeFamily("family-1", RevokeReason.DEACTIVATED, NOW);
    }

    @Test
    void aSessionOfAnOldRoleEnds() {
        givenStoredToken(Role.CUSTOMER, NOW.plus(TTL));
        User nowAnOwner = customer(UserStatus.ACTIVE);
        nowAnOwner.setRole(Role.RESTAURANT_ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(nowAnOwner));

        assertThatThrownBy(() -> refreshTokenService.rotate(PRESENTED, Role.CUSTOMER))
                .isInstanceOf(SessionEndedException.class);
        verify(refreshTokenRepository).revokeFamily("family-1", RevokeReason.ROLE_CHANGED, NOW);
    }

    @Test
    void endingASessionRevokesItsFamily() {
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(PRESENTED)))
                .thenReturn(Optional.of(token(Role.CUSTOMER, NOW.plus(TTL))));

        refreshTokenService.endSession(PRESENTED, RevokeReason.LOGOUT);

        verify(refreshTokenRepository).revokeFamily("family-1", RevokeReason.LOGOUT, NOW);
    }

    @Test
    void endingAnUnknownSessionIsNotAnError() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        refreshTokenService.endSession(PRESENTED, RevokeReason.LOGOUT);

        verify(refreshTokenRepository, never()).revokeFamily(anyString(), any(), any());
    }

    @Test
    void endingAllSessionsOfAUserRevokesEveryActiveToken() {
        refreshTokenService.endAllSessions(1L, RevokeReason.PASSWORD_CHANGED);

        verify(refreshTokenRepository).revokeAllOfUser(1L, RevokeReason.PASSWORD_CHANGED, NOW);
    }

    @Test
    void cleanupKeepsExpiredTokensForOneMoreLifetime() {
        refreshTokenService.deleteExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredBefore(NOW.minus(TTL));
    }

    @Test
    void issuedTokenToStringHidesTheToken() {
        assertThat(new IssuedRefreshToken("secret-value", NOW).toString()).doesNotContain("secret-value");
    }

    private RefreshToken givenStoredToken(Role role, Instant expiresAt) {
        RefreshToken token = token(role, expiresAt);
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenService.hash(PRESENTED)))
                .thenReturn(Optional.of(token));
        return token;
    }

    private static RefreshToken token(Role role, Instant expiresAt) {
        return withId(new RefreshToken(1L, role, "family-1", RefreshTokenService.hash(PRESENTED),
                NOW.minus(Duration.ofDays(1)), expiresAt), 7L);
    }

    private static User customer(UserStatus status) {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        return user;
    }

    private static RefreshToken withId(RefreshToken token, Long id) {
        set(token, "id", id);
        return token;
    }

    /** Sets a field the entity only lets JPA set (ids, revocation done by bulk updates). */
    private static void set(RefreshToken token, String fieldName, Object value) {
        try {
            Field field = RefreshToken.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(token, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
