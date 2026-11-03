-- Tracing (Phase 14): the trace context (W3C traceparent) of the request or event that wrote an event, so the
-- relay sends the event in that same trace: one trace from the customer's request to every consumer.
ALTER TABLE outbox_events
    ADD COLUMN trace_parent VARCHAR(55) NULL;
