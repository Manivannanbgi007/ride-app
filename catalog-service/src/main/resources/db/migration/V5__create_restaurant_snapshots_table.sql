-- Local copy of the restaurant data catalog-service needs (owner, name, status), kept up to date
-- from RestaurantChanged events. Menus keep working while restaurant-service is down.
-- restaurant-service stays the owner of this data: the copy is never edited here.
CREATE TABLE restaurant_snapshots
(
    restaurant_id  BIGINT       NOT NULL,
    owner_user_id  BIGINT       NOT NULL,
    name           VARCHAR(150) NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    -- Restaurant version in restaurant-service that this copy shows; older events are ignored.
    source_version BIGINT       NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT pk_restaurant_snapshots PRIMARY KEY (restaurant_id)
);
