-- Delivery statistics count deliveries completed in a period (admin dashboard and reports)
CREATE INDEX idx_deliveries_status_delivered ON deliveries (status, delivered_at);
