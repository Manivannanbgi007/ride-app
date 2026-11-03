CREATE TABLE addresses
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    line1       VARCHAR(255) NOT NULL,
    line2       VARCHAR(255) NULL,
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20)  NOT NULL,
    country     VARCHAR(100) NOT NULL,
    latitude    DECIMAL(9, 6) NULL,
    longitude   DECIMAL(9, 6) NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_addresses_user_id ON addresses (user_id);
