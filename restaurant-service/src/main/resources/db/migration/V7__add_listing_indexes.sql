-- Two lists read the whole table in the order they show, and no index served that order (measured with 300,000
-- restaurants: 346 ms and 111 ms for one page, 0.3 ms with the index; see docs/search-architecture.md):
--  * the storefront's restaurant list: ACTIVE restaurants, A-Z (the default of every visitor)
--  * the admin's restaurant list: all restaurants, newest first (status_created needs a status filter)
CREATE INDEX idx_restaurants_status_name ON restaurants (status, name);
CREATE INDEX idx_restaurants_created ON restaurants (created_at);
