-- An order keeps COPIES of everything that may change later elsewhere (item names and prices,
-- restaurant name, delivery address), so order history never changes after the fact.
CREATE TABLE orders
(
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    customer_id          BIGINT         NOT NULL,
    customer_name        VARCHAR(100)   NOT NULL,
    customer_phone       VARCHAR(20)    NULL,
    restaurant_id        BIGINT         NOT NULL,
    restaurant_name      VARCHAR(150)   NOT NULL,
    status               VARCHAR(30)    NOT NULL,
    payment_method       VARCHAR(30)    NOT NULL,
    payment_status       VARCHAR(20)    NOT NULL,
    items_total          DECIMAL(10, 2) NOT NULL,
    delivery_fee         DECIMAL(10, 2) NOT NULL,
    tax_amount           DECIMAL(10, 2) NOT NULL,
    total_amount         DECIMAL(10, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    delivery_line1       VARCHAR(255)   NOT NULL,
    delivery_line2       VARCHAR(255)   NULL,
    delivery_city        VARCHAR(100)   NOT NULL,
    delivery_state       VARCHAR(100)   NOT NULL,
    delivery_postal_code VARCHAR(20)    NOT NULL,
    delivery_country     VARCHAR(100)   NOT NULL,
    delivery_latitude    DECIMAL(9, 6)  NULL,
    delivery_longitude   DECIMAL(9, 6)  NULL,
    notes                VARCHAR(500)   NULL,
    cancellation_reason  VARCHAR(500)   NULL,
    cancelled_by         VARCHAR(20)    NULL,
    -- Idempotency: the same customer + key always means the same order (retries never duplicate it).
    idempotency_key      VARCHAR(64)    NOT NULL,
    request_fingerprint  VARCHAR(64)    NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_idempotency UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at);
CREATE INDEX idx_orders_restaurant_status_created ON orders (restaurant_id, status, created_at);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);
CREATE INDEX idx_orders_created ON orders (created_at);

CREATE TABLE order_items
(
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL,
    menu_item_id BIGINT         NOT NULL,
    name         VARCHAR(120)   NOT NULL,
    food_type    VARCHAR(10)    NOT NULL,
    unit_price   DECIMAL(10, 2) NOT NULL,
    quantity     INT            NOT NULL,
    line_total   DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
