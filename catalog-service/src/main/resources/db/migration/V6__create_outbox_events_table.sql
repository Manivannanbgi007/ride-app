-- Transactional outbox: events are written here in the same transaction as the change they describe,
-- then OutboxRelay publishes them to Kafka. If Kafka is down, nothing is lost: rows wait until it is back.
CREATE TABLE outbox_events
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_id       VARCHAR(36)  NOT NULL,
    -- Avro full name, e.g. com.fooddelivery.events.user.UserRegistered
    event_type     VARCHAR(150) NOT NULL,
    -- What the event is about, for searching (e.g. MENU_ITEM 42)
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    message_key    VARCHAR(100) NOT NULL,
    -- Avro binary of the event, plus the schema it was written with (so old rows still decode after a schema change)
    payload        MEDIUMBLOB   NOT NULL,
    avro_schema    TEXT         NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    -- NULL until Kafka has acknowledged the event
    published_at   DATETIME(6)  NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500) NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_event_id UNIQUE (event_id)
);

-- The relay reads "unpublished, oldest first"
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (published_at, id);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);
