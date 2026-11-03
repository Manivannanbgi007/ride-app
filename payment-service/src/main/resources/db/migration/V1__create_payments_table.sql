CREATE TABLE payments
(
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    -- Order and customer live in other services: ids only.
    order_id              BIGINT         NOT NULL,
    customer_id           BIGINT         NOT NULL,
    amount                DECIMAL(10, 2) NOT NULL,
    refunded_amount       DECIMAL(10, 2) NOT NULL DEFAULT 0,
    currency              VARCHAR(3)     NOT NULL,
    method                VARCHAR(20)    NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    -- Masked description only (for example "VISA **** 1111"). Card numbers are never stored.
    instrument            VARCHAR(60)    NULL,
    provider_reference    VARCHAR(64)    NULL,
    failure_reason        VARCHAR(255)   NULL,
    idempotency_key       VARCHAR(64)    NOT NULL,
    request_fingerprint   VARCHAR(64)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    processing_started_at DATETIME(6)    NULL,
    completed_at          DATETIME(6)    NULL,
    updated_at            DATETIME(6)    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    -- The order id while the payment is in progress or successful, NULL otherwise. The unique key on it
    -- means an order can never have two running/successful payments: no double charging, even under races.
    active_order_id       BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('INITIATED', 'PROCESSING', 'SUCCESS') THEN order_id END) STORED,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT uk_payments_active_order UNIQUE (active_order_id)
);

CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_customer_created ON payments (customer_id, created_at);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);
CREATE INDEX idx_payments_created ON payments (created_at);
