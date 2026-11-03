package com.fooddelivery.searchservice.kafka;

import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

/**
 * While an event is processed, every log line carries its eventId and requestId, and the orderId, paymentId and
 * deliveryId it is about (when the event has those fields). Picked up by Boot's Kafka listener container factory;
 * the trace and span ids come from Kafka's observation (tracing).
 */
@Component
public class EventLogContext implements RecordInterceptor<Object, Object> {

    static final List<String> FIELDS = List.of("eventId", "requestId", "orderId", "paymentId", "deliveryId");

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> consumerRecord,
                                                    Consumer<Object, Object> consumer) {
        if (consumerRecord.value() instanceof SpecificRecord event) {
            for (String name : FIELDS) {
                Schema.Field field = event.getSchema().getField(name);
                Object value = field == null ? null : event.get(field.pos());
                if (value != null) {
                    MDC.put(name, value.toString());
                }
            }
        }
        return consumerRecord;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> consumerRecord, Consumer<Object, Object> consumer) {
        FIELDS.forEach(MDC::remove);
    }

    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
        FIELDS.forEach(MDC::remove);
    }
}
