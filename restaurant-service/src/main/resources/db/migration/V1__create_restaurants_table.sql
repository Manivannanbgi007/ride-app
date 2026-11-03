CREATE TABLE restaurants
(
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    -- Id of the owner in user-service. No foreign key: users live in another service's database.
    owner_user_id           BIGINT        NOT NULL,
    name                    VARCHAR(150)  NOT NULL,
    description             VARCHAR(1000) NULL,
    cuisines                VARCHAR(255)  NOT NULL,
    phone                   VARCHAR(20)   NOT NULL,
    email                   VARCHAR(255)  NOT NULL,
    address_line1           VARCHAR(255)  NOT NULL,
    address_line2           VARCHAR(255)  NULL,
    city                    VARCHAR(100)  NOT NULL,
    state                   VARCHAR(100)  NOT NULL,
    postal_code             VARCHAR(20)   NOT NULL,
    country                 VARCHAR(100)  NOT NULL,
    latitude                DECIMAL(9, 6) NULL,
    longitude               DECIMAL(9, 6) NULL,
    time_zone               VARCHAR(50)   NOT NULL,
    legal_business_name     VARCHAR(200)  NOT NULL,
    tax_registration_number VARCHAR(50)   NULL,
    food_license_number     VARCHAR(50)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    status_reason           VARCHAR(500)  NULL,
    status_changed_at       DATETIME(6)   NOT NULL,
    approved_at             DATETIME(6)   NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_restaurants PRIMARY KEY (id)
);

CREATE INDEX idx_restaurants_owner ON restaurants (owner_user_id);
-- Public listing: ACTIVE restaurants in a city. Admin listing: by status, newest first.
CREATE INDEX idx_restaurants_status_city ON restaurants (status, city);
CREATE INDEX idx_restaurants_status_created ON restaurants (status, created_at);
