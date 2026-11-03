CREATE TABLE refunds
(
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    payment_id           BIGINT         NOT NULL,
    order_id             BIGINT         NOT NULL,
    amount               DECIMAL(10, 2) NOT NULL,
    reason               VARCHAR(500)   NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    requested_by_type    VARCHAR(20)    NOT NULL,
    requested_by_user_id BIGINT         NULL,
    provider_reference   VARCHAR(64)    NULL,
    failure_reason       VARCHAR(255)   NULL,
    -- Idempotency: the same key for the same payment always means the same refund.
    idempotency_key      VARCHAR(64)    NOT NULL,
    requested_at         DATETIME(6)    NOT NULL,
    completed_at         DATETIME(6)    NULL,
    updated_at           DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT uk_refunds_idempotency UNIQUE (payment_id, idempotency_key)
);

CREATE INDEX idx_refunds_status_requested ON refunds (status, requested_at);
CREATE INDEX idx_refunds_order ON refunds (order_id);
