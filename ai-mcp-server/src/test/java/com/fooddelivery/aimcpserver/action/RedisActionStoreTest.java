package com.fooddelivery.aimcpserver.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.aimcpserver.dto.Link;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.security.Role;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/** Against a real Redis (Testcontainers): an action reads back exactly as stored, expires, and its lock is SET NX. */
@Testcontainers(disabledWithoutDocker = true)
class RedisActionStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static RedisActionStore store;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        store = new RedisActionStore(new StringRedisTemplate(factory), JsonMapper.builder().build());
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    private static ActionRequest action(String id) {
        Instant now = Instant.parse("2026-10-09T06:00:00Z");
        return new ActionRequest(id, 8, Role.DELIVERY_PARTNER, ActionKind.MARK_DELIVERED, null, 31,
                new ActionBody(null, null, null, new BigDecimal("562.90")),
                new PendingAction.Details("Delivered: delivery #31", List.of(new PendingAction.Row("Cash",
                        "Collect 562.90 INR")), "Confirm only after collecting exactly 562.90 INR in cash.", "Delivered"),
                "Delivery #31 is marked delivered.", new Link("Open delivery", "/partner/deliveries/31"),
                ActionRequest.Status.NEW, now, now.plus(Duration.ofMinutes(10)), null);
    }

    @Test
    void roundTripKeepsEveryFieldAndExpiresAfterTheTtl() throws Exception {
        String id = "act_6f1c2b9e-1d1a-4c55-9a52-0c4b1f2f7a10";
        store.save(action(id), Duration.ofSeconds(1));

        ActionRequest read = store.find(id).orElseThrow();

        assertThat(read).isEqualTo(action(id));
        assertThat(read.body().cashCollected()).isEqualByComparingTo("562.90");
        assertThat(read.path()).isEqualTo("/api/v1/delivery/deliveries/mine/31/deliver");
        Thread.sleep(1500);
        assertThat(store.find(id)).isEmpty();
    }

    @Test
    void lockIsSetIfAbsentAndCanBeReleased() {
        String id = "act_0d3c7a41-58f4-4b8e-8a8f-6d5b2c1e9f00";

        assertThat(store.tryLock(id, Duration.ofSeconds(5))).isTrue();
        assertThat(store.tryLock(id, Duration.ofSeconds(5))).isFalse();
        store.unlock(id);
        assertThat(store.tryLock(id, Duration.ofSeconds(5))).isTrue();
        store.unlock(id);
    }
}
