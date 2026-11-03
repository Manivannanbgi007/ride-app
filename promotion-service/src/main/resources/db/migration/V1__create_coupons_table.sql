-- Coupons created by platform admins. A coupon is usable when it is ACTIVE, today is within its validity dates
-- (in the business time zone) and its usage limit is not reached.
CREATE TABLE coupons
(
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    -- What customers type, stored in capitals (WELCOME100)
    code               VARCHAR(30)    NOT NULL,
    description        VARCHAR(255)   NULL,
    -- PERCENTAGE (discount_value is a percent, optionally capped by max_discount) or FIXED (an amount)
    discount_type      VARCHAR(20)    NOT NULL,
    discount_value     DECIMAL(10, 2) NOT NULL,
    max_discount       DECIMAL(10, 2) NULL,
    -- Items total (before discount) the order must reach
    min_order_amount   DECIMAL(10, 2) NOT NULL DEFAULT 0,
    -- NULL: every restaurant; otherwise only this restaurant's orders
    restaurant_id      BIGINT         NULL,
    valid_from         DATE           NOT NULL,
    valid_until        DATE           NOT NULL,
    -- NULL: unlimited
    usage_limit        INT            NULL,
    per_customer_limit INT            NULL,
    -- Uses that count against the limits right now (reserved, applied to an order, or redeemed)
    times_used         INT            NOT NULL DEFAULT 0,
    -- ACTIVE or INACTIVE (by an admin)
    status             VARCHAR(20)    NOT NULL,
    created_by         BIGINT         NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL,
    CONSTRAINT pk_coupons PRIMARY KEY (id),
    CONSTRAINT uk_coupons_code UNIQUE (code)
);

CREATE INDEX idx_coupons_status_validity ON coupons (status, valid_from, valid_until);
CREATE INDEX idx_coupons_restaurant ON coupons (restaurant_id);
