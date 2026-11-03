package com.fooddelivery.promotionservice.kafka.producer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Keeps a business transaction in one trace across the outbox. The relay sends events later, from its own thread,
 * so the trace context of the request (or event) that wrote an event is stored with it (W3C traceparent) and the
 * relay sends the event in a span of that same trace. Kafka's observation then passes it on in the message headers.
 * Without tracing (e.g. tests) both methods simply do nothing extra.
 */
@Component
public class OutboxTracing {

    private static final String TRACEPARENT = "traceparent";

    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public OutboxTracing(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** The current trace as a W3C traceparent ("00-traceId-spanId-01"), or null when there is none. */
    public String currentTraceParent() {
        Tracer currentTracer = tracer.getIfAvailable();
        Propagator currentPropagator = propagator.getIfAvailable();
        if (currentTracer == null || currentPropagator == null) {
            return null;
        }
        TraceContext context = currentTracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        currentPropagator.inject(context, carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /** Runs the work in a new span that continues the given trace (or just runs it, without one). */
    @SuppressWarnings("checkstyle:IllegalCatch") // records any failure on the span, then rethrows it unchanged
    public <T> T inTraceOf(String traceParent, String spanName, Callable<T> work) throws Exception {
        Tracer currentTracer = tracer.getIfAvailable();
        Propagator currentPropagator = propagator.getIfAvailable();
        if (traceParent == null || currentTracer == null || currentPropagator == null) {
            return work.call();
        }
        Span span = currentPropagator.extract(Map.of(TRACEPARENT, traceParent), Map::get)
                .name(spanName)
                .start();
        try (Tracer.SpanInScope scope = currentTracer.withSpan(span)) {
            return work.call();
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
