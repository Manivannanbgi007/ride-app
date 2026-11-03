-- The restaurant owner's "which partners delivered for me" report reads one restaurant's DELIVERED deliveries in a
-- date range. idx_deliveries_restaurant (restaurant_id, created_at) cannot narrow by delivery day, and
-- idx_deliveries_status_delivered (status, delivered_at) would read every restaurant's deliveries of those days.
CREATE INDEX idx_deliveries_restaurant_delivered ON deliveries (restaurant_id, status, delivered_at);
