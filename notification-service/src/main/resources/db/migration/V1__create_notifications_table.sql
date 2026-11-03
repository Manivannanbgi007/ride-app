-- Every notification the platform sends: one row per recipient and channel.
-- Rows are created from Kafka events and sent by NotificationDispatcher, which retries failures.
CREATE TABLE notifications
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    -- The event that caused it; one event can produce several notifications (people, channels)
    source_event_id   VARCHAR(36)   NOT NULL,
    type              VARCHAR(50)   NOT NULL,
    channel           VARCHAR(10)   NOT NULL,
    -- Who receives it: a user, or a restaurant whose owner is looked up when sending
    recipient_user_id BIGINT        NULL,
    restaurant_id     BIGINT        NULL,
    order_id          BIGINT        NULL,
    -- Email address, phone number or in-app inbox actually used (known once sent)
    recipient_address VARCHAR(255)  NULL,
    subject           VARCHAR(255)  NOT NULL,
    body              VARCHAR(2000) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    attempts          INT           NOT NULL DEFAULT 0,
    -- When the dispatcher may (re)try it; also a short lease while one dispatcher is sending it
    next_attempt_at   DATETIME(6)   NULL,
    last_error        VARCHAR(500)  NULL,
    request_id        VARCHAR(64)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    sent_at           DATETIME(6)   NULL,
    -- In-app (PUSH) notifications: when the user read it
    read_at           DATETIME(6)   NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    -- The same event never notifies the same way twice
    CONSTRAINT uk_notifications_event_type_channel UNIQUE (source_event_id, type, channel)
);

CREATE INDEX idx_notifications_due ON notifications (status, next_attempt_at);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_user_id, created_at);
CREATE INDEX idx_notifications_created ON notifications (created_at);
CREATE INDEX idx_notifications_order ON notifications (order_id);
