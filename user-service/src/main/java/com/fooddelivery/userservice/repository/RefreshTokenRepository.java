package com.fooddelivery.userservice.repository;

import java.time.Instant;
import java.util.Optional;

import com.fooddelivery.userservice.entity.RefreshToken;
import com.fooddelivery.userservice.entity.RevokeReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Loads a token and locks its row until the transaction ends. Two tabs refreshing with the same token at the same
     * moment are handled one after the other: the second one sees that the first already exchanged it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /** Ends one sign-in session: every token of the family that is still active. Returns how many were revoked. */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(String familyId, RevokeReason reason, Instant now);

    /** Ends every sign-in session of a user (all areas, all browsers). Returns how many tokens were revoked. */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason
            where t.userId = :userId and t.revokedAt is null
            """)
    int revokeAllOfUser(Long userId, RevokeReason reason, Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
