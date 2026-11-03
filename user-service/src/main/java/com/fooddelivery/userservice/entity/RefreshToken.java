package com.fooddelivery.userservice.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One refresh token of a sign-in session. Only the SHA-256 hash of the token is stored; the token itself is in the
 * user's HttpOnly cookie. All tokens of one sign-in share a family id: each refresh replaces the token with a new one.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private RevokeReason revokeReason;

    public RefreshToken(Long userId, Role role, String familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.role = role;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean wasUsed() {
        return usedAt != null;
    }

    /** True when the token was exchanged no longer than {@code grace} ago (a parallel refresh, e.g. a second tab). */
    public boolean wasUsedWithin(Duration grace, Instant now) {
        return usedAt != null && !usedAt.plus(grace).isBefore(now);
    }

    /** The token was exchanged for {@code successor}: it can never be used again. */
    public void markUsed(Instant now, Long successorId) {
        this.usedAt = now;
        this.replacedById = successorId;
    }
}
