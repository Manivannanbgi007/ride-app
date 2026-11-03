package com.fooddelivery.restaurantservice.config;

import com.fooddelivery.events.EventContracts;
import com.fooddelivery.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics this service publishes to. Kafka does not create topics automatically
 * (auto-create is off), so KafkaAdmin creates the missing ones at startup.
 */
@Configuration
public class KafkaConfig {

    static {
        // Avro only creates classes from trusted packages: trust the shared event classes.
        EventContracts.trustEventClasses();
    }

    @Bean
    public NewTopic restaurantChangedTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(Topics.RESTAURANT_CHANGED)
                .partitions(topics.partitions())
                .replicas(topics.replicas())
                .build();
    }
}
