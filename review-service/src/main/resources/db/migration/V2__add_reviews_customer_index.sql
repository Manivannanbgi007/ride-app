-- "Dishes this customer rated highest" (admin customer screen) finds a customer's reviews, optionally of a recent
-- period; without this index it would scan every review.
CREATE INDEX idx_reviews_customer_created ON reviews (customer_id, created_at);
