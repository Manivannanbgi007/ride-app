-- One use of a coupon. Reserved at checkout (so the limits hold even when many customers check out at once),
-- linked to its order by the OrderCreated event, redeemed when the order is delivered, or released (and the use
-- given back) when the order is cancelled or no order was ever placed with it.
CREATE TABLE coupon_redemptions
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    coupon_id       BIGINT         NOT NULL,
    coupon_code     VARCHAR(30)    NOT NULL,
    -- Chosen by order-service per checkout (customer + Idempotency-Key): a retried checkout gets the same reservation
    reservation_key VARCHAR(150)   NOT NULL,
    customer_id     BIGINT         NOT NULL,
    restaurant_id   BIGINT         NOT NULL,
    -- Known once the OrderCreated event arrives
    order_id        BIGINT         NULL,
    order_amount    DECIMAL(10, 2) NOT NULL,
    discount_amount DECIMAL(10, 2) NOT NULL,
    -- RESERVED, APPLIED (order placed), REDEEMED (order delivered) or RELEASED
    status          VARCHAR(20)    NOT NULL,
    release_reason  VARCHAR(255)   NULL,
    reserved_at     DATETIME(6)    NOT NULL,
    applied_at      DATETIME(6)    NULL,
    redeemed_at     DATETIME(6)    NULL,
    released_at     DATETIME(6)    NULL,
    version         BIGINT         NOT NULL,
    CONSTRAINT pk_coupon_redemptions PRIMARY KEY (id),
    CONSTRAINT uk_coupon_redemptions_reservation UNIQUE (reservation_key),
    CONSTRAINT uk_coupon_redemptions_order UNIQUE (order_id),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id)
);

-- Per-customer limit: "how many uses of this coupon does this customer have"
CREATE INDEX idx_coupon_redemptions_customer ON coupon_redemptions (coupon_id, customer_id, status);
-- Usage monitoring and the clean-up of reservations no order was placed with
CREATE INDEX idx_coupon_redemptions_status ON coupon_redemptions (status, reserved_at);
