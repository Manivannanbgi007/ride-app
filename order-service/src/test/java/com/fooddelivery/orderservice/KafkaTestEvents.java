package com.fooddelivery.orderservice;

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
    static <T> T awaitEvent(ConsumerFactory<String, Object> consumerFactory, String topic, String key, Class<T> type) {
        return awaitEvents(consumerFactory, topic, key, type, 1).getFirst();
    }

    /**
     * Reads the topic from the start until {@code count} events of the given type with the given key
     * were found. Events with the same key are in one partition, so they come back in the order sent.
     */
    static <T> List<T> awaitEvents(ConsumerFactory<String, Object> consumerFactory, String topic, String key,
                                   Class<T> type, int count) {
        return awaitRecords(consumerFactory, topic, key, type, count).stream()
                .map(consumerRecord -> type.cast(consumerRecord.value()))
                .toList();
    }

    /** Like {@link #awaitEvents}, but the whole records (with their headers). */
    static List<ConsumerRecord<String, Object>> awaitRecords(ConsumerFactory<String, Object> consumerFactory,
                                                             String topic, String key, Class<?> type, int count) {
        List<ConsumerRecord<String, Object>> records = new ArrayList<>();
        try (Consumer<String, Object> consumer = consumerFactory.createConsumer("test-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (records.size() < count && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, Object> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(consumerRecord.key()) && type.isInstance(consumerRecord.value())) {
                        records.add(consumerRecord);
                    }
                }
            }
        }
        if (records.size() < count) {
            throw new AssertionError("Expected " + count + " " + type.getSimpleName() + " events with key " + key
                    + " on topic " + topic + " but found " + records.size());
        }
        return records;
    }
}
