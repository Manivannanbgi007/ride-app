-- The platform-wide audit log: a copy of every audit record the services save for admin actions (they send them as
-- AdminActionAudited events, through their outbox), plus admin-service's own records. Column sizes match the
-- services' admin_audit_log tables.
CREATE TABLE audit_records
(
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    source_service  VARCHAR(50)   NOT NULL,
    -- Id of the record in the service that saved it (NULL for admin-service's own records)
    source_audit_id BIGINT        NULL,
    event_id        VARCHAR(36)   NULL,
    admin_user_id   BIGINT        NOT NULL,
    action          VARCHAR(60)   NOT NULL,
    entity_type     VARCHAR(50)   NOT NULL,
    entity_id       VARCHAR(64)   NOT NULL,
    old_value       VARCHAR(1000) NULL,
    new_value       VARCHAR(1000) NULL,
    reason          VARCHAR(500)  NULL,
    ip_address      VARCHAR(45)   NULL,
    request_id      VARCHAR(64)   NULL,
    -- When the admin acted (from the source service)
    audited_at      DATETIME(6)   NOT NULL,
    -- When the copy arrived here
    received_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_audit_records PRIMARY KEY (id),
    -- A record that Kafka delivers twice, or that a service replays, is stored once
    CONSTRAINT uk_audit_records_source UNIQUE (source_service, source_audit_id)
);

-- Search: newest first, by admin, entity, action, service or request
CREATE INDEX idx_audit_records_audited ON audit_records (audited_at);
CREATE INDEX idx_audit_records_admin ON audit_records (admin_user_id, audited_at);
CREATE INDEX idx_audit_records_entity ON audit_records (entity_type, entity_id, audited_at);
CREATE INDEX idx_audit_records_action ON audit_records (action, audited_at);
CREATE INDEX idx_audit_records_service ON audit_records (source_service, audited_at);
CREATE INDEX idx_audit_records_request ON audit_records (request_id);
