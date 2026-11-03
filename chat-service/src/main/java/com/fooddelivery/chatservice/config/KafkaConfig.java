package com.fooddelivery.chatservice.config;

import com.fooddelivery.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The topics this service publishes to (created if missing, so it can start before notification-service reads them).
 * Chat only publishes: nothing in this service consumes events.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic chatMessageSentTopic(KafkaTopicProperties topics) {
        return topic(Topics.CHAT_MESSAGE_SENT, topics);
    }

    @Bean
    public NewTopic chatMessagesReadTopic(KafkaTopicProperties topics) {
        return topic(Topics.CHAT_MESSAGES_READ, topics);
    }

    private static NewTopic topic(String name, KafkaTopicProperties topics) {
        return TopicBuilder.name(name).partitions(topics.partitions()).replicas(topics.replicas()).build();
    }
}
