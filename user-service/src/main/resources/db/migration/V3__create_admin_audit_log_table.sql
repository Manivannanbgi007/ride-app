-- Immutable record of administrator actions.
-- The application only ever inserts into this table (no update or delete code paths).
CREATE TABLE admin_audit_log
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT        NOT NULL,
    action        VARCHAR(60)   NOT NULL,
    entity_type   VARCHAR(50)   NOT NULL,
    entity_id     VARCHAR(64)   NOT NULL,
    old_value     VARCHAR(1000) NULL,
    new_value     VARCHAR(1000) NULL,
    reason        VARCHAR(500)  NULL,
    ip_address    VARCHAR(45)   NULL,
    request_id    VARCHAR(64)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_admin_audit_log_entity ON admin_audit_log (entity_type, entity_id, created_at);
CREATE INDEX idx_admin_audit_log_admin ON admin_audit_log (admin_user_id, created_at);
