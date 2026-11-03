CREATE TABLE menu_items
(
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    -- Restaurant lives in restaurant-service: only its id is stored, no foreign key.
    restaurant_id BIGINT         NOT NULL,
    category_id   BIGINT         NOT NULL,
    name          VARCHAR(120)   NOT NULL,
    description   VARCHAR(500)   NULL,
    price         DECIMAL(10, 2) NOT NULL,
    food_type     VARCHAR(10)    NOT NULL,
    image_url     VARCHAR(500)   NULL,
    available     BOOLEAN        NOT NULL DEFAULT TRUE,
    status        VARCHAR(20)    NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_menu_items PRIMARY KEY (id),
    -- Same database: a real foreign key. RESTRICT stops a category from being deleted while items use it.
    CONSTRAINT fk_menu_items_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    -- One dish name per restaurant (case-insensitive collation)
    CONSTRAINT uk_menu_items_restaurant_name UNIQUE (restaurant_id, name)
);

CREATE INDEX idx_menu_items_restaurant_status ON menu_items (restaurant_id, status);
CREATE INDEX idx_menu_items_category ON menu_items (category_id);
