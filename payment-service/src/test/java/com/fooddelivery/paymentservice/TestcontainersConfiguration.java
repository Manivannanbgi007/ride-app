package com.fooddelivery.paymentservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Starts a real MySQL 8.4 and a real Kafka in Docker for integration tests.
 * {@code @ServiceConnection} points the datasource and the Kafka clients at them automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4");
    }

    @Bean
    @ServiceConnection
    ConfluentKafkaContainer kafkaContainer() {
        return new ConfluentKafkaContainer("confluentinc/cp-kafka:8.2.4");
    }
}
