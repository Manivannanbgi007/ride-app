-- Public ratings: the average and the number of reviews per restaurant or dish are counted from these tables on every
-- request, so the grouped queries must be answered from an index without reading the rows.

-- Restaurant ratings (grouped by restaurant, over reviews.rating), the stars distribution of one restaurant, and the
-- public review list sorted by stars (highest / lowest first, then newest)
CREATE INDEX idx_reviews_restaurant_rating ON reviews (restaurant_id, rating, created_at);

-- Dish ratings (grouped by menu item, over review_dishes.rating) and the stars distribution of one dish
CREATE INDEX idx_review_dishes_menu_item_rating ON review_dishes (menu_item_id, rating);
