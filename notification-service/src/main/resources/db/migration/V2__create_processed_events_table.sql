-- Idempotent consumer: the id of every Kafka event this service has applied, written in the same
-- transaction as the change the event caused. A redelivered event finds its id here and is skipped.
CREATE TABLE processed_events
(
    event_id     VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(150) NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
