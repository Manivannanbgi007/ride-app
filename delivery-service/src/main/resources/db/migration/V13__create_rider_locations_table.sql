-- The latest GPS position of the partner on a delivery that is on its way (ACCEPTED or PICKED_UP).
-- One row per delivery, replaced by every new position: no route history is kept. The row is deleted soon after the
-- delivery is delivered, cancelled or given to another partner (RiderLocationService.deleteFinished).
-- A table of its own, not columns of deliveries: positions arrive every few seconds and must never clash with the
-- optimistic lock (version) of a delivery that a partner or an admin is changing at the same moment.
CREATE TABLE rider_locations
(
    delivery_id     BIGINT        NOT NULL,
    partner_user_id BIGINT        NOT NULL,
    latitude        DECIMAL(9, 6) NOT NULL,
    longitude       DECIMAL(9, 6) NOT NULL,
    -- How exact the phone says the position is (metres), when it says so
    accuracy_meters INT           NULL,
    -- Direction of travel, degrees clockwise from north, when the phone knows it (only while moving)
    heading_deg     SMALLINT      NULL,
    -- When delivery-service received the position (server time, not the phone's clock)
    received_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_rider_locations PRIMARY KEY (delivery_id),
    CONSTRAINT fk_rider_locations_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id) ON DELETE CASCADE
);
