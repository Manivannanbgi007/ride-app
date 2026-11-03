package com.fooddelivery.searchservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * Starts a real Elasticsearch (same version as docker-compose) and a real Kafka in Docker for integration tests;
 * {@code @ServiceConnection} points Spring Data Elasticsearch and the Kafka clients at them.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearchContainer() {
        return new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.4.5")
                // Development settings, as in docker-compose: no TLS/passwords, small heap,
                // and no index is ever created implicitly (only by SearchIndexes, with its mapping)
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withEnv("action.auto_create_index", "false");
    }

    @Bean
    @ServiceConnection
    ConfluentKafkaContainer kafkaContainer() {
        return new ConfluentKafkaContainer("confluentinc/cp-kafka:8.2.4");
    }
}
