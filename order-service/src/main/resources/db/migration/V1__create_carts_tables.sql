-- One cart per customer. A cart holds items of a single restaurant.
CREATE TABLE carts
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id   BIGINT      NOT NULL,
    restaurant_id BIGINT      NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uk_carts_customer UNIQUE (customer_id)
);

-- Only the menu item id and quantity are stored: names and prices are always read fresh from catalog-service.
CREATE TABLE cart_items
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id      BIGINT      NOT NULL,
    menu_item_id BIGINT      NOT NULL,
    quantity     INT         NOT NULL,
    added_at     DATETIME(6) NOT NULL,
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT uk_cart_items_item UNIQUE (cart_id, menu_item_id)
);
