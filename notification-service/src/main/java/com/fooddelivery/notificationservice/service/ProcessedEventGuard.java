package com.fooddelivery.notificationservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.fooddelivery.notificationservice.entity.ProcessedEvent;
import com.fooddelivery.notificationservice.repository.ProcessedEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer: Kafka delivers at least once, so an event can arrive twice. Each event id is recorded
 * in the same transaction as the change it causes; a repeated event is recognised and skipped. If two
 * deliveries race, the primary key lets only one transaction commit, and the other is retried and skipped.
 */
@Slf4j
@Component
public class ProcessedEventGuard {

    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;
    private final Duration retention;

    public ProcessedEventGuard(ProcessedEventRepository processedEventRepository, Clock clock,
                               @Value("${app.processed-events.retention:30d}") Duration retention) {
        this.processedEventRepository = processedEventRepository;
        this.clock = clock;
        this.retention = retention;
    }

    /** @return true the first time this event is seen (and records it), false for a repeated delivery */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String eventId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Repeated event ignored. event={} eventId={}", eventType, eventId);
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, clock.instant()));
        return true;
    }

    /** Kafka keeps events for 7 days, so ids older than the retention can never come back. */
    @Scheduled(cron = "${app.processed-events.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteOldEntries() {
        Instant before = clock.instant().minus(retention);
        int deleted = processedEventRepository.deleteProcessedBefore(before);
        if (deleted > 0) {
            log.info("Old processed-event ids deleted. count={}", deleted);
        }
    }
}
