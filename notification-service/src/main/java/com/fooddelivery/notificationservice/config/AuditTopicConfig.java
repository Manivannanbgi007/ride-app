package com.fooddelivery.notificationservice.config;

import com.fooddelivery.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The platform-wide audit topic: every service with admin actions writes to it, admin-service reads it. */
@Configuration
public class AuditTopicConfig {

    @Bean
    public NewTopic adminActionAuditedTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(Topics.ADMIN_ACTION_AUDITED)
                .partitions(topics.partitions())
                .replicas(topics.replicas())
                .build();
    }
}
