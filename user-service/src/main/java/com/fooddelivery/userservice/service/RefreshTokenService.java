package com.fooddelivery.userservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens: the long-lived half of a sign-in session.
 * <ul>
 *   <li>A refresh token is 256 random bits. Only its SHA-256 hash is stored.</li>
 *   <li>Rotation: every refresh exchanges the token for a new one in the same family (one family = one sign-in).</li>
 *   <li>Reuse detection: an exchanged token presented again later than the grace period means a copy was stolen, so
 *       the whole family is revoked: thief and user both have to sign in again.</li>
 * </ul>
 * Tokens and hashes are never logged; log lines use the user id and the family id.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               AppProperties appProperties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /** A new refresh token (the raw value goes into the cookie, never into the database or a log). */
    public record IssuedRefreshToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedRefreshToken[value=****, expiresAt=" + expiresAt + "]";
        }
    }

    /** Result of a refresh: the user the session belongs to and the token that replaces the presented one. */
    public record Rotation(User user, IssuedRefreshToken refreshToken) {
    }

    /** Starts a new session (family) for a user who just signed in. */
    @Transactional
    public IssuedRefreshToken startSession(User user) {
        return issue(user.getId(), user.getRole(), UUID.randomUUID().toString(), clock.instant()).issued();
    }

    /**
     * Exchanges a refresh token for a new one. The checks run with the token's row locked, so two parallel refreshes
     * with the same token are decided one after the other.
     *
     * @throws SessionEndedException     unknown, expired or revoked token, another area's token, a deactivated user or
     *                                   a changed role, or a stolen copy (then the whole session is revoked)
     * @throws RefreshConflictException  the token was exchanged a moment ago by a parallel request of the same browser
     */
    // Revocations made just before throwing must be kept, so these exceptions do not roll the transaction back
    @Transactional(noRollbackFor = {SessionEndedException.class, RefreshConflictException.class})
    public Rotation rotate(String presentedToken, Role area) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(presentedToken))
                .orElseThrow(SessionEndedException::new);

        if (token.isRevoked() || token.isExpired(now) || token.getRole() != area) {
            throw new SessionEndedException();
        }
        if (token.wasUsed()) {
            if (token.wasUsedWithin(appProperties.auth().refreshToken().reuseGrace(), now)) {
                throw new RefreshConflictException();
            }
            refreshTokenRepository.revokeFamily(token.getFamilyId(), RevokeReason.REUSE_DETECTED, now);
            log.warn("Refresh token reused after it was exchanged: session revoked. userId={} family={}",
                    token.getUserId(), token.getFamilyId());
            throw new SessionEndedException();
        }

        User user = userRepository.findById(token.getUserId()).orElse(null);
        RevokeReason accountProblem = accountProblem(user, token.getRole());
        if (accountProblem != null) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), accountProblem, now);
            log.info("Session ended because the account changed. userId={} reason={}", token.getUserId(), accountProblem);
            throw new SessionEndedException();
        }

        Stored successor = issue(user.getId(), user.getRole(), token.getFamilyId(), now);
        token.markUsed(now, successor.entity().getId());
        return new Rotation(user, successor.issued());
    }

    /** Signs out one session (logout, or replaced by a new sign-in). Unknown or ended tokens are ignored. */
    @Transactional
    public void endSession(String presentedToken, RevokeReason reason) {
        refreshTokenRepository.findByTokenHash(hash(presentedToken)).ifPresent(token -> {
            int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId(), reason, clock.instant());
            if (revoked > 0) {
                log.info("Session ended. userId={} reason={}", token.getUserId(), reason);
            }
        });
    }

    /** Signs a user out everywhere (password changed, account deactivated, role changed). */
    @Transactional
    public void endAllSessions(Long userId, RevokeReason reason) {
        int revoked = refreshTokenRepository.revokeAllOfUser(userId, reason, clock.instant());
        if (revoked > 0) {
            log.info("All sessions of the user ended. userId={} reason={} tokens={}", userId, reason, revoked);
        }
    }

    /**
     * Deletes tokens that expired more than one token lifetime ago. Keeping them a while longer lets a late replay of
     * an old stolen token still be recognised as reuse.
     */
    @Scheduled(cron = "${app.auth.refresh-token.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteExpiredTokens() {
        Instant cutoff = clock.instant().minus(appProperties.auth().refreshToken().ttl());
        int deleted = refreshTokenRepository.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("Expired refresh tokens deleted. count={}", deleted);
        }
    }

    /** SHA-256 (hex) of a token: what the database stores and looks up. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Why the account can no longer use this session, or null when it can. */
    private static RevokeReason accountProblem(User user, Role sessionRole) {
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return RevokeReason.DEACTIVATED;
        }
        return user.getRole() == sessionRole ? null : RevokeReason.ROLE_CHANGED;
    }

    private Stored issue(Long userId, Role role, String familyId, Instant now) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant expiresAt = now.plus(appProperties.auth().refreshToken().ttl());

        RefreshToken saved = refreshTokenRepository.save(
                new RefreshToken(userId, role, familyId, hash(value), now, expiresAt));
        return new Stored(saved, new IssuedRefreshToken(value, expiresAt));
    }

    private record Stored(RefreshToken entity, IssuedRefreshToken issued) {
    }
}
