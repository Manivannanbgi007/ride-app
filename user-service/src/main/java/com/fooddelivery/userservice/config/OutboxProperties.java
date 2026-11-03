package com.fooddelivery.userservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Transactional outbox settings (see application.yml).
 *
 * @param relayEnabled run the relay that publishes outbox events to Kafka
 * @param pollInterval how often the relay looks for new events
 * @param batchSize    events published per database transaction
 * @param sendTimeout  how long to wait for Kafka to acknowledge one event
 * @param maxBackoff   longest pause after failures (the pause doubles with each failed attempt)
 * @param retention    how long published events are kept for tracing before they are deleted
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(@DefaultValue("true") boolean relayEnabled,
                               @DefaultValue("500ms") Duration pollInterval,
                               @DefaultValue("100") int batchSize,
                               @DefaultValue("15s") Duration sendTimeout,
                               @DefaultValue("10s") Duration maxBackoff,
                               @DefaultValue("7d") Duration retention) {
}
