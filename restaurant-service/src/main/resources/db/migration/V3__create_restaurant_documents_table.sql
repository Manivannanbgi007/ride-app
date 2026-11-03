-- Onboarding documents (food license, tax registration, ...).
-- Only references are stored here; the files themselves live in document storage.
CREATE TABLE restaurant_documents
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT       NOT NULL,
    document_type VARCHAR(30)  NOT NULL,
    document_url  VARCHAR(500) NOT NULL,
    submitted_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_restaurant_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE INDEX idx_documents_restaurant ON restaurant_documents (restaurant_id);
