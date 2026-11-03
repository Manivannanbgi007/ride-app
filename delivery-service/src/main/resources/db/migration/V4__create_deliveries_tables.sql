-- One delivery per order, created when the restaurant reports the food ready.
-- Addresses are copies taken at that moment; customer and restaurant details may still be missing
-- (NULL) while their events are on the way: the assignment job fills them in before assigning.
CREATE TABLE deliveries
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_id            BIGINT         NOT NULL,
    customer_id         BIGINT         NULL,
    customer_name       VARCHAR(150)   NULL,
    customer_phone      VARCHAR(20)    NULL,
    restaurant_id       BIGINT         NOT NULL,
    restaurant_name     VARCHAR(150)   NULL,
    pickup_line1        VARCHAR(255)   NULL,
    pickup_line2        VARCHAR(255)   NULL,
    pickup_city         VARCHAR(100)   NULL,
    pickup_state        VARCHAR(100)   NULL,
    pickup_postal_code  VARCHAR(20)    NULL,
    pickup_country      VARCHAR(100)   NULL,
    pickup_latitude     DECIMAL(9, 6)  NULL,
    pickup_longitude    DECIMAL(9, 6)  NULL,
    dropoff_line1       VARCHAR(255)   NULL,
    dropoff_line2       VARCHAR(255)   NULL,
    dropoff_city        VARCHAR(100)   NULL,
    dropoff_state       VARCHAR(100)   NULL,
    dropoff_postal_code VARCHAR(20)    NULL,
    dropoff_country     VARCHAR(100)   NULL,
    dropoff_latitude    DECIMAL(9, 6)  NULL,
    dropoff_longitude   DECIMAL(9, 6)  NULL,
    payment_method      VARCHAR(30)    NULL,
    -- Cash the partner collects at the door (cash on delivery), otherwise 0
    cash_to_collect     DECIMAL(10, 2) NULL,
    currency            CHAR(3)        NULL,
    -- PENDING, ASSIGNED, ACCEPTED, PICKED_UP, DELIVERED or CANCELLED
    status              VARCHAR(20)    NOT NULL,
    partner_user_id     BIGINT         NULL,
    partner_name        VARCHAR(150)   NULL,
    vehicle_type        VARCHAR(20)    NULL,
    cash_collected      DECIMAL(10, 2) NULL,
    cancellation_reason VARCHAR(500)   NULL,
    ready_at            DATETIME(6)    NOT NULL,
    assigned_at         DATETIME(6)    NULL,
    accepted_at         DATETIME(6)    NULL,
    picked_up_at        DATETIME(6)    NULL,
    delivered_at        DATETIME(6)    NULL,
    cancelled_at        DATETIME(6)    NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL,
    CONSTRAINT pk_deliveries PRIMARY KEY (id),
    CONSTRAINT uk_deliveries_order UNIQUE (order_id)
);

-- Assignment job: waiting deliveries, oldest first; monitoring: deliveries by status and age
CREATE INDEX idx_deliveries_status_ready ON deliveries (status, ready_at);
CREATE INDEX idx_deliveries_partner ON deliveries (partner_user_id, created_at);
CREATE INDEX idx_deliveries_customer ON deliveries (customer_id);
CREATE INDEX idx_deliveries_restaurant ON deliveries (restaurant_id, created_at);

-- Everything that happened to a delivery, in order (append only)
CREATE TABLE delivery_events
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    delivery_id     BIGINT       NOT NULL,
    -- CREATED, ASSIGNED, ACCEPTED, DECLINED, EXPIRED, REASSIGNED, PICKED_UP, DELIVERED, CANCELLED
    event_type      VARCHAR(20)  NOT NULL,
    -- Status of the delivery after the event
    status          VARCHAR(20)  NOT NULL,
    -- The partner the event is about (for example the one who declined)
    partner_user_id BIGINT       NULL,
    -- SYSTEM, DELIVERY_PARTNER or ADMIN, and the user who acted
    actor_type      VARCHAR(20)  NOT NULL,
    actor_user_id   BIGINT       NULL,
    note            VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_delivery_events PRIMARY KEY (id),
    CONSTRAINT fk_delivery_events_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id)
);

CREATE INDEX idx_delivery_events_delivery ON delivery_events (delivery_id, id);
-- "Partners who declined or ignored this delivery are not offered it again"
CREATE INDEX idx_delivery_events_partner ON delivery_events (delivery_id, partner_user_id, event_type);
