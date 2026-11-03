CREATE TABLE users
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NULL,
    role          VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    last_login_at DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone)
);

-- Admin user listing filters by role/status and sorts by creation date.
CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_users_created_at ON users (created_at);
