-- The admin's food list shows all menu items, most recently changed first (its default): no index had that order.
CREATE INDEX idx_menu_items_updated ON menu_items (updated_at);
