package com.fooddelivery.notificationservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Starts a real MySQL 8.4, a real Kafka and a Mailpit mail server in Docker for integration tests.
 * {@code @ServiceConnection} points the datasource and the Kafka clients at them; the mail settings
 * point at Mailpit, whose HTTP API lets the tests read the emails that were sent.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final int SMTP_PORT = 1025;
    static final int MAILPIT_API_PORT = 8025;

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
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>("axllent/mailpit:v1.31.4")
                .withExposedPorts(SMTP_PORT, MAILPIT_API_PORT)
                .waitingFor(Wait.forHttp("/readyz").forPort(MAILPIT_API_PORT));
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(SMTP_PORT));
        };
    }
}
