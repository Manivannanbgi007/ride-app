package com.fooddelivery.userservice.kafka.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fooddelivery.events.AvroBinary;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.user.UserRegistered;
import com.fooddelivery.userservice.config.OutboxProperties;
import com.fooddelivery.userservice.entity.OutboxEvent;
import com.fooddelivery.userservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final int BATCH_SIZE = 10;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(true, Duration.ofMillis(500), BATCH_SIZE,
                Duration.ofSeconds(1), Duration.ofSeconds(10), Duration.ofDays(7));
        // Unit test: the transaction manager is a stub, TransactionTemplate just runs the callback.
        outboxRelay = new OutboxRelay(outboxEventRepository, kafkaTemplate, properties,
                new OutboxTracing(new StaticListableBeanFactory().getBeanProvider(Tracer.class),
                        new StaticListableBeanFactory().getBeanProvider(Propagator.class)),
                new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
    }

    @Test
    void eventsArePublishedOldestFirstAsTheyWereWritten() {
        OutboxEvent first = outboxEvent(1L);
        OutboxEvent second = outboxEvent(2L);
        when(outboxEventRepository.lockOldestUnpublished(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq(Topics.USER_REGISTERED), any(), any())).thenAnswer(invocation -> acknowledged());

        outboxRelay.publishPending();

        InOrder inOrder = inOrder(kafkaTemplate);
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(kafkaTemplate).send(eq(Topics.USER_REGISTERED), eq("1"), sent.capture());
        inOrder.verify(kafkaTemplate).send(eq(Topics.USER_REGISTERED), eq("2"), any());
        assertThat(((GenericRecord) sent.getValue()).get("userId")).isEqualTo(1L);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
        assertThat(first.getAttempts()).isEqualTo(1);
    }

    @Test
    void publishedEventsOlderThanTheRetentionAreDeleted() {
        when(outboxEventRepository.deletePublishedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

        outboxRelay.deleteOldPublishedEvents();

        verify(outboxEventRepository).deletePublishedBefore(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void aFailureStopsTheBatchKeepsTheEventAndPausesTheRelay() {
        OutboxEvent first = outboxEvent(1L);
        OutboxEvent second = outboxEvent(2L);
        when(outboxEventRepository.lockOldestUnpublished(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq(Topics.USER_REGISTERED), eq("1"), any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("Broker not available")));

        outboxRelay.publishPending();
        outboxRelay.publishPending(); // still paused: the clock has not moved

        verify(kafkaTemplate, never()).send(any(), eq("2"), any());
        verify(outboxEventRepository, times(1)).lockOldestUnpublished(BATCH_SIZE);
        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("Broker not available");
        assertThat(second.getAttempts()).isZero();
    }

    private static OutboxEvent outboxEvent(Long userId) {
        UserRegistered event = UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(NOW)
                .setUserId(userId)
                .setFullName("User " + userId)
                .setEmail("user" + userId + "@example.com")
                .setRole("CUSTOMER")
                .build();
        return OutboxEvent.create(event.getEventId(), event.getSchema().getFullName(), "USER", userId,
                Topics.USER_REGISTERED, String.valueOf(userId), AvroBinary.encode(event),
                event.getSchema().toString(), NOW, null);
    }

    private static CompletableFuture<SendResult<String, Object>> acknowledged() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(Topics.USER_REGISTERED, 0), 0L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>(Topics.USER_REGISTERED, "x"), metadata));
    }
}
