-- Messages to order-service ("payment 12 for order 34 succeeded"), written in the SAME transaction as the
-- payment result. They are sent right after commit and retried until order-service accepts them.
-- This is the Outbox pattern over HTTP; Phase 8 replaces the HTTP call with Kafka.
CREATE TABLE order_payment_updates
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    order_id   BIGINT       NOT NULL,
    payment_id BIGINT       NOT NULL,
    outcome    VARCHAR(20)  NOT NULL,
    reason     VARCHAR(255) NULL,
    status     VARCHAR(20)  NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6)  NOT NULL,
    sent_at    DATETIME(6)  NULL,
    updated_at DATETIME(6)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_order_payment_updates PRIMARY KEY (id)
);

CREATE INDEX idx_order_payment_updates_status ON order_payment_updates (status, created_at);
