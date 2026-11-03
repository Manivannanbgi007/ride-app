-- Delivery partners (riders). The account lives in user-service: user_id is its id, and the row is created from the
-- UserRegistered event (or on the partner's first call, if the event has not arrived yet). Partners were kept by
-- delivery-service until this service took them over; delivery-service keeps a copy for assigning deliveries.
CREATE TABLE delivery_partners
(
    user_id              BIGINT       NOT NULL,
    full_name            VARCHAR(150) NOT NULL,
    phone                VARCHAR(20)  NULL,
    -- Filled in by the partner; all three are required before going online
    vehicle_type         VARCHAR(20)  NULL,
    vehicle_number       VARCHAR(20)  NULL,
    city                 VARCHAR(100) NULL,
    -- ACTIVE or SUSPENDED: only a platform admin changes it (audited)
    account_status       VARCHAR(20)  NOT NULL,
    suspension_reason    VARCHAR(500) NULL,
    -- Copy of what delivery-service reports (DeliveryPartnerStatusChanged): OFFLINE, AVAILABLE, BUSY or SUSPENDED
    work_status          VARCHAR(20)  NOT NULL,
    work_status_reason   VARCHAR(500) NULL,
    current_delivery_id  BIGINT       NULL,
    available_since      DATETIME(6)  NULL,
    completed_deliveries INT          NOT NULL DEFAULT 0,
    -- Version of delivery-service's record the copy was taken from: older reports are ignored
    work_status_version  BIGINT       NULL,
    -- What the partner and admins see: SUSPENDED while the account is suspended, otherwise the work status
    status               VARCHAR(20)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL,
    CONSTRAINT pk_delivery_partners PRIMARY KEY (user_id)
);

-- Admin search: "available partners in this city" (the reassign dialog), partners by status
CREATE INDEX idx_delivery_partners_status_city ON delivery_partners (status, city);
CREATE INDEX idx_delivery_partners_city ON delivery_partners (city);
