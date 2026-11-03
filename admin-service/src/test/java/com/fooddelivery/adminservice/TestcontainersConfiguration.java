package com.fooddelivery.adminservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Starts a real MySQL 8.4 and a real Kafka in Docker for integration tests;
 * {@code @ServiceConnection} points the datasource and the Kafka clients at them.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        // As in docker-compose.yml: lets the service's own user create the triggers that keep audit records immutable
        return new MySQLContainer("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
    }

    @Bean
    @ServiceConnection
    ConfluentKafkaContainer kafkaContainer() {
        return new ConfluentKafkaContainer("confluentinc/cp-kafka:8.2.4");
    }
}
