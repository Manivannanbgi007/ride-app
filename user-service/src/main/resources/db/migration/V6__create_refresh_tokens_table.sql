-- Refresh tokens (sign-in sessions). The raw token lives only in the user's HttpOnly cookie; this table keeps its
-- SHA-256 hash, so a database leak does not give anyone a working session.
-- One sign-in starts a "family"; every refresh replaces the token with a new one in the same family (rotation).
CREATE TABLE refresh_tokens
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    role           VARCHAR(30) NOT NULL,
    family_id      VARCHAR(36) NOT NULL,
    token_hash     VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    expires_at     DATETIME(6) NOT NULL,
    -- Set when the token was exchanged for a new one; using it again later means it was stolen
    used_at        DATETIME(6) NULL,
    replaced_by_id BIGINT      NULL,
    revoked_at     DATETIME(6) NULL,
    -- LOGOUT, REPLACED, REUSE_DETECTED, PASSWORD_CHANGED, DEACTIVATED, ROLE_CHANGED
    revoke_reason  VARCHAR(30) NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_revoked ON refresh_tokens (user_id, revoked_at);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
