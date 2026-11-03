-- The admin's delivery list shows all deliveries, newest first (its default). The existing indexes all start with a
-- filter column (partner, restaurant, status), so without a filter the whole table was sorted for every page.
CREATE INDEX idx_deliveries_created ON deliveries (created_at);
