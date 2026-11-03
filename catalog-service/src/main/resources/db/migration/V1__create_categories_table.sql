-- Global food categories managed by platform admins (Pizza, Biryani, Desserts, ...).
CREATE TABLE categories
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(60)  NOT NULL,
    description   VARCHAR(255) NULL,
    image_url     VARCHAR(500) NULL,
    display_order INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    -- The column collation is case-insensitive, so "Pizza" and "pizza" are the same name.
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE INDEX idx_categories_status_order ON categories (status, display_order);
