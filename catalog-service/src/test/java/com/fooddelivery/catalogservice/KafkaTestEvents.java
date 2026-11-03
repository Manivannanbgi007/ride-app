package com.fooddelivery.catalogservice;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.ConsumerFactory;

/** Reads the events that a test caused from a Kafka topic. */
final class KafkaTestEvents {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private KafkaTestEvents() {
    }

    /** Waits for the first event of the given type with the given message key. */
    static <T> T awaitEvent(ConsumerFactory<Object, Object> consumerFactory, String topic, String key, Class<T> type) {
        return awaitEvents(consumerFactory, topic, key, type, 1).getFirst();
    }

    /**
     * Reads the topic from the start until {@code count} events of the given type with the given key
     * were found. Events with the same key are in one partition, so they come back in the order sent.
     */
    static <T> List<T> awaitEvents(ConsumerFactory<Object, Object> consumerFactory, String topic, String key,
                                   Class<T> type, int count) {
        List<T> events = new ArrayList<>();
        try (Consumer<Object, Object> consumer = consumerFactory.createConsumer("test-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (events.size() < count && System.nanoTime() < deadline) {
                for (ConsumerRecord<Object, Object> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(consumerRecord.key()) && type.isInstance(consumerRecord.value())) {
                        events.add(type.cast(consumerRecord.value()));
                    }
                }
            }
        }
        if (events.size() < count) {
            throw new AssertionError("Expected " + count + " " + type.getSimpleName() + " events with key " + key
                    + " on topic " + topic + " but found " + events.size());
        }
        return events;
    }
}
