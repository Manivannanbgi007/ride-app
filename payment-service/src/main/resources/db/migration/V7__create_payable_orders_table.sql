-- payment-service's copy of the orders it may be asked to charge, fed by OrderCreated and OrderCancelled events.
-- Customers can pay without payment-service calling order-service (order-service is asked only about
-- orders whose OrderCreated event has not arrived yet). order-service stays the owner of order data.
CREATE TABLE payable_orders
(
    order_id       BIGINT         NOT NULL,
    customer_id    BIGINT         NOT NULL,
    restaurant_id  BIGINT         NOT NULL,
    payment_method VARCHAR(30)    NOT NULL,
    total_amount   DECIMAL(10, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    -- The order's status as far as payment matters: PAYMENT_PENDING (can be paid), PLACED (cash on delivery),
    -- CANCELLED or REJECTED (can no longer be paid)
    status         VARCHAR(30)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    CONSTRAINT pk_payable_orders PRIMARY KEY (order_id)
);
