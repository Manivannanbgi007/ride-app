package com.fooddelivery.restaurantservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Starts a real MySQL 8.4, Kafka and Redis in Docker for integration tests.
 * {@code @ServiceConnection} points the datasource, the Kafka clients and the Redis client at them automatically.
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

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);
    }
}
