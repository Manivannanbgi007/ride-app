-- Where orders are picked up: a copy of each restaurant's name and address (from RestaurantChanged;
-- restaurant-service owns restaurants). source_version is the restaurant's version: older events are ignored.
CREATE TABLE pickup_points
(
    restaurant_id    BIGINT        NOT NULL,
    name             VARCHAR(150)  NOT NULL,
    line1            VARCHAR(255)  NOT NULL,
    line2            VARCHAR(255)  NULL,
    city             VARCHAR(100)  NOT NULL,
    state            VARCHAR(100)  NOT NULL,
    postal_code      VARCHAR(20)   NOT NULL,
    country          VARCHAR(100)  NOT NULL,
    latitude         DECIMAL(9, 6) NULL,
    longitude        DECIMAL(9, 6) NULL,
    source_version   BIGINT        NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_pickup_points PRIMARY KEY (restaurant_id)
);
