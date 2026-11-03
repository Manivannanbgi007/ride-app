-- Customer reviews of delivered orders: one review per order, with optional tags and per-dish ratings.
-- Names are copied (snapshots) from the order when the review is written, so later renames never change a review.
CREATE TABLE reviews
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    -- One review per order (the unique key also settles two simultaneous requests)
    order_id        BIGINT       NOT NULL,
    customer_id     BIGINT       NOT NULL,
    customer_name   VARCHAR(120) NOT NULL,
    restaurant_id   BIGINT       NOT NULL,
    restaurant_name VARCHAR(120) NOT NULL,
    -- Overall rating, 1 to 5 stars
    rating          TINYINT      NOT NULL,
    comment         VARCHAR(1000) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT uk_reviews_order UNIQUE (order_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

-- Admin list per restaurant, newest first; list and summary over a period; filter by stars
CREATE INDEX idx_reviews_restaurant_created ON reviews (restaurant_id, created_at);
CREATE INDEX idx_reviews_created ON reviews (created_at);
CREATE INDEX idx_reviews_rating ON reviews (rating);

-- What the customer liked: the names of the ReviewTag enum (at most 4 per review, each once)
CREATE TABLE review_tags
(
    review_id BIGINT      NOT NULL,
    tag       VARCHAR(30) NOT NULL,
    CONSTRAINT pk_review_tags PRIMARY KEY (review_id, tag),
    CONSTRAINT fk_review_tags_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE
);

-- Optional rating of single dishes of the order
CREATE TABLE review_dishes
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    review_id    BIGINT       NOT NULL,
    menu_item_id BIGINT       NOT NULL,
    dish_name    VARCHAR(120) NOT NULL,
    rating       TINYINT      NOT NULL,
    CONSTRAINT pk_review_dishes PRIMARY KEY (id),
    CONSTRAINT uk_review_dishes UNIQUE (review_id, menu_item_id),
    CONSTRAINT fk_review_dishes_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT ck_review_dishes_rating CHECK (rating BETWEEN 1 AND 5)
);

-- Featured dishes: average rating per menu item
CREATE INDEX idx_review_dishes_menu_item ON review_dishes (menu_item_id);
