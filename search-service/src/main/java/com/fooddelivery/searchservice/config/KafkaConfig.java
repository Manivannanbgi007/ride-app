package com.fooddelivery.searchservice.config;

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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer setup: the topics this service indexes (created if missing, so it can start before the
 * producers), their dead-letter topics, and what happens when an event cannot be applied.
 */
@Configuration
public class KafkaConfig {

    /** Restaurants, categories and menu items: everything that is searchable. */
    public static final List<String> CONSUMED_TOPICS = List.of(
            Topics.RESTAURANT_CHANGED,
            Topics.CATEGORY_CHANGED,
            Topics.MENU_ITEM_CHANGED);

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
     * Elasticsearch unavailable (down, restarting, overloaded): the event is retried until it works, waiting
     * 1s, 2s, 4s ... up to 30s between attempts. The partition waits meanwhile, so events stay in order and none
     * is lost; the index catches up when Elasticsearch is back. Any other failure is a problem with the event
     * itself: it goes to "&lt;topic&gt;.DLT" at once, with the error in its headers, and indexing moves on.
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

        ExponentialBackOff untilElasticsearchIsBack = new ExponentialBackOff(1_000L, 2.0);
        untilElasticsearchIsBack.setMaxInterval(30_000L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, untilElasticsearchIsBack);
        errorHandler.defaultFalse();
        errorHandler.addRetryableExceptions(DataAccessResourceFailureException.class, TransientDataAccessException.class);
        return errorHandler;
    }
}
