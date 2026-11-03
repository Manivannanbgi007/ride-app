-- delivery-service's copy of what an order needs for its delivery (from OrderCreated; order-service owns orders).
-- The delivery itself is created only when the restaurant reports the food ready.
CREATE TABLE order_details
(
    order_id            BIGINT         NOT NULL,
    customer_id         BIGINT         NOT NULL,
    customer_name       VARCHAR(150)   NULL,
    customer_phone      VARCHAR(20)    NULL,
    restaurant_id       BIGINT         NOT NULL,
    restaurant_name     VARCHAR(150)   NULL,
    dropoff_line1       VARCHAR(255)   NOT NULL,
    dropoff_line2       VARCHAR(255)   NULL,
    dropoff_city        VARCHAR(100)   NOT NULL,
    dropoff_state       VARCHAR(100)   NOT NULL,
    dropoff_postal_code VARCHAR(20)    NOT NULL,
    dropoff_country     VARCHAR(100)   NOT NULL,
    dropoff_latitude    DECIMAL(9, 6)  NULL,
    dropoff_longitude   DECIMAL(9, 6)  NULL,
    payment_method      VARCHAR(30)    NOT NULL,
    total_amount        DECIMAL(10, 2) NOT NULL,
    currency            CHAR(3)        NOT NULL,
    received_at         DATETIME(6)    NOT NULL,
    CONSTRAINT pk_order_details PRIMARY KEY (order_id)
);
