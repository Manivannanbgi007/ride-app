package com.fooddelivery.catalogservice.config;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fooddelivery.events.EventContracts;
import com.fooddelivery.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka setup: the topics this service publishes to (menu items and categories, for search), the topic it reads
 * (created if missing, so it can start before the producer) with its dead-letter topic, and what happens when an
 * event cannot be processed.
 */
@Configuration
public class KafkaConfig {

    static {
        // Avro only creates classes from trusted packages: trust the shared event classes.
        EventContracts.trustEventClasses();
    }

    // Published

    @Bean
    public NewTopic menuItemChangedTopic(KafkaTopicProperties topics) {
        return topic(Topics.MENU_ITEM_CHANGED, topics);
    }

    @Bean
    public NewTopic categoryChangedTopic(KafkaTopicProperties topics) {
        return topic(Topics.CATEGORY_CHANGED, topics);
    }

    // Consumed

    @Bean
    public NewTopic restaurantChangedTopic(KafkaTopicProperties topics) {
        return topic(Topics.RESTAURANT_CHANGED, topics);
    }

    @Bean
    public NewTopic restaurantChangedDeadLetterTopic(KafkaTopicProperties topics) {
        return topic(Topics.RESTAURANT_CHANGED + Topics.DEAD_LETTER_SUFFIX, topics);
    }

    /**
     * A failing event is retried twice, one second apart; then it is copied to "&lt;topic&gt;.DLT" with the
     * error in its headers and the consumer moves on. Events that cannot even be read (not valid Avro)
     * go to the dead-letter topic at once. Picked up by Boot's listener container factory.
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

    private static NewTopic topic(String name, KafkaTopicProperties topics) {
        return TopicBuilder.name(name)
                .partitions(topics.partitions())
                .replicas(topics.replicas())
                .build();
    }
}
