-- Which payment gateway took the payment, and the gateway's own order id (Razorpay: "order_...").
-- Existing payments were taken by the built-in simulated gateway.
ALTER TABLE payments
    ADD COLUMN provider          VARCHAR(20) NOT NULL DEFAULT 'SIMULATED' AFTER method,
    ADD COLUMN provider_order_id VARCHAR(64) NULL AFTER provider_reference;

-- One payment per Razorpay order: a verification or webhook finds exactly one payment, and a Razorpay order can
-- never be attached to two payments.
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_provider_order UNIQUE (provider_order_id);
