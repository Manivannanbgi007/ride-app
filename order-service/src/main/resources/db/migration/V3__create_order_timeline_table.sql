-- Append-only history of status changes: who moved the order to which status, when and why.
CREATE TABLE order_timeline
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    actor_type    VARCHAR(20)  NOT NULL,
    actor_user_id BIGINT       NULL,
    note          VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_order_timeline PRIMARY KEY (id),
    CONSTRAINT fk_order_timeline_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_order_timeline_order ON order_timeline (order_id, created_at);
