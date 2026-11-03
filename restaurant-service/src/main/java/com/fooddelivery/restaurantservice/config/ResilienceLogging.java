package com.fooddelivery.restaurantservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One log line when a circuit changes state, e.g. "Circuit for catalog-service: State transition from CLOSED to OPEN". */
@Slf4j
@Configuration
public class ResilienceLogging {

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitStateLogging() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                logStateChanges(event.getAddedEntry());
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
                // nothing to clean up
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
                logStateChanges(event.getNewEntry());
            }
        };
    }

    private static void logStateChanges(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(transition ->
                log.warn("Circuit for {}: {}", circuitBreaker.getName(), transition.getStateTransition()));
    }
}
