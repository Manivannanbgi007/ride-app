package com.fooddelivery.notificationservice.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fooddelivery.events.EventContracts;
import com.fooddelivery.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer setup: the topics this service reads (created if missing, so it can start before the
 * producers), their dead-letter topics, and what happens when an event cannot be processed.
 */
@Configuration
public class KafkaConfig {

    /** Every event this service reads: notifications and live updates. */
    public static final List<String> CONSUMED_TOPICS = List.of(
            Topics.USER_REGISTERED,
            Topics.RESTAURANT_CHANGED,
            Topics.ORDER_STATUS_CHANGED,
            Topics.ORDER_CANCELLED,
            Topics.PAYMENT_STATUS_CHANGED,
            Topics.DELIVERY_STATUS_CHANGED,
            // Chat events: only turned into live hints (LiveEventsConsumer); declared here so they exist at start-up
            Topics.CHAT_MESSAGE_SENT,
            Topics.CHAT_MESSAGES_READ);

    static {
        // Avro only creates classes from trusted packages: trust the shared event classes.
        EventContracts.trustEventClasses();
    }

    @Bean
    public KafkaAdmin.NewTopics consumedTopics(KafkaTopicProperties topics) {
        return new KafkaAdmin.NewTopics(CONSUMED_TOPICS.stream()
                .flatMap(name -> Stream.of(name, name + Topics.DEAD_LETTER_SUFFIX))
                .map(name -> TopicBuilder.name(name).partitions(topics.partitions()).replicas(topics.replicas()).build())
                .toArray(NewTopic[]::new));
    }

    /**
     * A failing event is retried twice, one second apart; then it is copied to "&lt;topic&gt;.DLT" with the
     * error in its headers (including this consumer group) and the consumer moves on. Events that cannot even
     * be read (not valid Avro) go to the dead-letter topic at once. Picked up by Boot's listener container factory.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 ProducerFactory<Object, Object> producerFactory) {
        // Unreadable events are raw bytes and need a bytes serializer; readable ones are Avro records.
        KafkaTemplate<Object, Object> rawBytesTemplate = new KafkaTemplate<>(producerFactory.copyWithConfigurationOverride(
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));
        Map<Class<?>, KafkaOperations<?, ?>> templatesByValueType = new LinkedHashMap<>();
        templatesByValueType.put(byte[].class, rawBytesTemplate);
        templatesByValueType.put(Object.class, kafkaTemplate);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templatesByValueType,
                (consumerRecord, exception) -> new TopicPartition(
                        consumerRecord.topic() + Topics.DEAD_LETTER_SUFFIX, consumerRecord.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2L));
    }
}
