-- Revenue of one dish over time (admin Food Detail chart) looks order lines up by menu item; without this index
-- it would scan every order line. order_id is included so the join to orders needs no extra row lookups.
CREATE INDEX idx_order_items_menu_item ON order_items (menu_item_id, order_id);
