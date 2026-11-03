package com.fooddelivery.promotionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Size of the Kafka topics this service creates (see application.yml).
 *
 * @param partitions how many consumers of one group can work in parallel
 * @param replicas   copies of each partition: 1 for the local single-node Kafka, 3 in production
 */
@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicProperties(@DefaultValue("3") int partitions,
                                   @DefaultValue("1") int replicas) {
}
