-- Phase 8: payment results reach order-service as PaymentStatusChanged events through the transactional
-- outbox (outbox_events), so the Phase 6 HTTP outbox is no longer used.
-- Deploy only when it has no PENDING rows left (the old retry job has delivered them all).
DROP TABLE order_payment_updates;
