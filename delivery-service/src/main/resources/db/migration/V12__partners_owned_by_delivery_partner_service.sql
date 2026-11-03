-- Delivery partners are owned by delivery-partner-service since event-contracts 1.7.0. This table stays
-- delivery-service's dispatch list: what a partner is doing (OFFLINE, AVAILABLE, BUSY), the queue order and the
-- current delivery are decided here, together with the deliveries. Name, phone, vehicle and city are a copy
-- (DeliveryPartnerChanged), and so is the suspension (an admin decision in delivery-partner-service).
ALTER TABLE delivery_partners
    -- Version of the partner in delivery-partner-service this copy was taken from (older events are ignored);
    -- NULL for partners from before the move, which accept any version
    ADD COLUMN profile_version   BIGINT       NULL,
    -- Suspended: no new deliveries; a delivery already accepted is finished first, then the partner is SUSPENDED
    ADD COLUMN suspended         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN suspension_reason VARCHAR(500) NULL;

-- Partners suspended before the move stay suspended
UPDATE delivery_partners
SET suspended = TRUE, suspension_reason = status_reason
WHERE status = 'SUSPENDED';
