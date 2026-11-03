package com.fooddelivery.deliveryservice.kafka.producer;

import java.time.Clock;

import com.fooddelivery.deliveryservice.entity.OutboxEvent;
import com.fooddelivery.deliveryservice.repository.OutboxEventRepository;
import com.fooddelivery.events.AvroBinary;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an event to the transactional outbox. It must run inside the business transaction
 * (MANDATORY): the change and its event are committed together, or not at all.
 * {@link OutboxRelay} publishes the event to Kafka afterwards.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxTracing outboxTracing;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, Long aggregateId, String topic, String messageKey, SpecificRecord event) {
        Schema schema = event.getSchema();
        outboxEventRepository.save(OutboxEvent.create(eventIdOf(event), schema.getFullName(), aggregateType,
                aggregateId, topic, messageKey, AvroBinary.encode(event), schema.toString(), clock.instant(),
                outboxTracing.currentTraceParent()));
    }

    /** Every event in event-contracts has an eventId field (consumers use it to ignore duplicates). */
    private static String eventIdOf(SpecificRecord event) {
        Schema.Field field = event.getSchema().getField("eventId");
        if (field == null || event.get(field.pos()) == null) {
            throw new IllegalArgumentException(event.getSchema().getFullName() + " has no eventId");
        }
        return event.get(field.pos()).toString();
    }
}
