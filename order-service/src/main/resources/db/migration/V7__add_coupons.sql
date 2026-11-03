-- Coupons (promotion-service). The cart remembers the code the customer applied; the discount is worked out
-- again whenever the cart is shown, because prices and the coupon's terms may change in the meantime.
ALTER TABLE carts
    ADD COLUMN coupon_code VARCHAR(30) NULL;

-- An order keeps the discount it was placed with, and the id of the coupon use reserved for it in
-- promotion-service (given back if the order is cancelled).
ALTER TABLE orders
    ADD COLUMN discount_amount       DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER items_total,
    ADD COLUMN coupon_code           VARCHAR(30)    NULL AFTER discount_amount,
    ADD COLUMN coupon_reservation_id BIGINT         NULL AFTER coupon_code;
