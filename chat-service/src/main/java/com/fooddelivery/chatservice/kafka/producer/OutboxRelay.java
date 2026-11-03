package com.fooddelivery.chatservice.kafka.producer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.fooddelivery.chatservice.config.OutboxProperties;
import com.fooddelivery.chatservice.entity.OutboxEvent;
import com.fooddelivery.chatservice.repository.OutboxEventRepository;
import com.fooddelivery.events.AvroBinary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox events to Kafka, oldest first, and marks each one published once Kafka has
 * acknowledged it. A failure stops the batch (later events must not overtake it) and pauses the relay,
 * doubling the pause up to {@code maxBackoff}; the event stays in the table and is retried.
 * <p>
 * Delivery is at least once: if the service stops right after Kafka acknowledged an event but before
 * the row was marked, the event is sent again. Consumers ignore duplicates by eventId.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxProperties properties;
    private final OutboxTracing outboxTracing;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, Schema> parsedSchemas = new ConcurrentHashMap<>();

    /** Only the scheduler thread reads and writes it. */
    private Instant pausedUntil = Instant.MIN;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate,
                       OutboxProperties properties, OutboxTracing outboxTracing, MeterRegistry meterRegistry,
                       Clock clock, PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.outboxTracing = outboxTracing;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // READ COMMITTED: the locking read takes no gap locks, so business transactions can keep
        // inserting new outbox rows while the relay is sending.
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // Events not sent yet: grows while Kafka is unreachable (counted in the database when Prometheus asks)
        Gauge.builder("outbox.events.waiting", outboxEventRepository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Events written to the outbox but not yet sent to Kafka")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    public void publishPending() {
        if (clock.instant().isBefore(pausedUntil)) {
            return;
        }
        int published;
        do {
            published = publishNextBatch();
        } while (published == properties.batchSize());
    }

    /** @return how many events were published (fewer than the batch size: nothing more to do for now) */
    int publishNextBatch() {
        return transactionTemplate.execute(transaction -> {
            List<OutboxEvent> batch = outboxEventRepository.lockOldestUnpublished(properties.batchSize());
            int count = 0;
            for (OutboxEvent event : batch) {
                if (!publish(event)) {
                    break;
                }
                count++;
            }
            return count;
        });
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 0 * * * *}")
    public void deleteOldPublishedEvents() {
        Instant before = clock.instant().minus(properties.retention());
        int deleted = transactionTemplate.execute(transaction -> outboxEventRepository.deletePublishedBefore(before));
        if (deleted > 0) {
            log.info("Old published outbox events deleted. count={}", deleted);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // any failure marks the event for a retry: nothing may stop the relay
    private boolean publish(OutboxEvent event) {
        try {
            Schema schema = parsedSchemas.computeIfAbsent(event.getAvroSchema(), json -> new Schema.Parser().parse(json));
            // Sent in the trace of the request that wrote the event: one trace from checkout to every consumer
            outboxTracing.inTraceOf(event.getTraceParent(), "outbox publish " + event.getTopic(), () -> {
                SendResult<String, Object> result = kafkaTemplate
                        .send(event.getTopic(), event.getMessageKey(), AvroBinary.decode(event.getPayload(), schema))
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(clock.instant());
                meterRegistry.counter("outbox.events.published", "topic", event.getTopic()).increment();
                log.info("Event published. event={} key={} eventId={} partition={} offset={}", event.eventName(),
                        event.getMessageKey(), event.getEventId(), result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                return result;
            });
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            event.markFailed(cause.getClass().getSimpleName() + ": " + cause.getMessage());
            meterRegistry.counter("outbox.publish.failures", "topic", event.getTopic()).increment();
            Duration pause = backoff(event.getAttempts());
            pausedUntil = clock.instant().plus(pause);
            log.warn("Event not published yet, will retry. event={} key={} eventId={} attempts={} retryIn={} reason={}",
                    event.eventName(), event.getMessageKey(), event.getEventId(), event.getAttempts(), pause,
                    event.getLastError());
            return false;
        }
    }

    /** 1s, 2s, 4s, ... up to maxBackoff. */
    private Duration backoff(int attempts) {
        long seconds = 1L << Math.clamp(attempts - 1L, 0, 20);
        Duration pause = Duration.ofSeconds(seconds);
        return pause.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : pause;
    }
}
