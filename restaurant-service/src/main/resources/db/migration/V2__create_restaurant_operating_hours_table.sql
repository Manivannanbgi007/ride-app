-- Weekly opening hours. A day can have several slots (for example lunch and dinner).
-- A day without rows means the restaurant is closed that day.
CREATE TABLE restaurant_operating_hours
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT      NOT NULL,
    day_of_week   VARCHAR(10) NOT NULL,
    opens_at      TIME        NOT NULL,
    closes_at     TIME        NOT NULL,
    CONSTRAINT pk_restaurant_operating_hours PRIMARY KEY (id),
    CONSTRAINT fk_operating_hours_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE INDEX idx_operating_hours_restaurant ON restaurant_operating_hours (restaurant_id);
