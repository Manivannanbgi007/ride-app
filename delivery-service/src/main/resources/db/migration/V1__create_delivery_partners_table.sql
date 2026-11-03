-- Delivery partners (riders). The account lives in user-service: user_id is its id, and the row is created
-- from the UserRegistered event (or on the partner's first call, if the event has not arrived yet).
CREATE TABLE delivery_partners
(
    user_id              BIGINT       NOT NULL,
    full_name            VARCHAR(150) NOT NULL,
    phone                VARCHAR(20)  NULL,
    -- Filled in by the partner; both are required before going online
    vehicle_type         VARCHAR(20)  NULL,
    vehicle_number       VARCHAR(20)  NULL,
    city                 VARCHAR(100) NULL,
    -- OFFLINE, AVAILABLE, BUSY (on a delivery) or SUSPENDED (by an admin)
    status               VARCHAR(20)  NOT NULL,
    status_reason        VARCHAR(500) NULL,
    current_delivery_id  BIGINT       NULL,
    -- When the partner last became available: the one waiting longest gets the next delivery
    available_since      DATETIME(6)  NULL,
    completed_deliveries INT          NOT NULL DEFAULT 0,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL,
    CONSTRAINT pk_delivery_partners PRIMARY KEY (user_id)
);

-- Assignment: "available partners in this city, longest waiting first"
CREATE INDEX idx_delivery_partners_assignment ON delivery_partners (status, city, available_since);
