-- The admin's refund list shows all refunds, newest first (its default); idx_refunds_status_requested needs a status.
CREATE INDEX idx_refunds_requested ON refunds (requested_at);
