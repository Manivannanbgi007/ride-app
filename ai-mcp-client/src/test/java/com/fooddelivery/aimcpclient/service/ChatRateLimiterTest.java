package com.fooddelivery.aimcpclient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.exception.TooManyRequestsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** Limits against a real Redis (Testcontainers); the "Redis is down" case uses a MOCKED template. */
class ChatRateLimiterTest {

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private ChatRateLimiter limiter;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);
        redisContainer.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        redisContainer.stop();
    }

    @BeforeEach
    void setUp() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        limiter = new ChatRateLimiter(redis, properties());
    }

    @Test
    void the21stMessageInTheWindowGets429WithRetryAfter() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire(1).close();
        }

        assertThatThrownBy(() -> limiter.acquire(1))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(TooManyRequestsException.REASON_RATE_LIMIT);
                    assertThat(e.getRetryAfterSeconds()).isBetween(1L, 600L);
                });
    }

    @Test
    void limitsAreKeptPerUser() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire(1).close();
        }

        assertThatCode(() -> limiter.acquire(2).close()).doesNotThrowAnyException();
    }

    @Test
    void aFourthSimultaneousChatGetsTheBusyMessageAndAFinishedChatFreesASlot() {
        ChatRateLimiter.Permit first = limiter.acquire(1);
        limiter.acquire(1);
        limiter.acquire(1);

        assertThatThrownBy(() -> limiter.acquire(1))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(TooManyRequestsException.REASON_BUSY);
                    assertThat(e.getMessage()).isEqualTo("The assistant is busy, please wait for your previous question");
                });

        first.close();
        assertThatCode(() -> limiter.acquire(1)).doesNotThrowAnyException();
    }

    @Test
    void aRefusedBusyChatDoesNotUseUpTheMessageQuota() {
        limiter.acquire(1);
        limiter.acquire(1);
        limiter.acquire(1);
        for (int i = 0; i < 30; i++) {
            assertThatThrownBy(() -> limiter.acquire(1)).isInstanceOf(TooManyRequestsException.class);
        }

        assertThat(redis.opsForValue().get("ai:rl:chat:1")).isEqualTo("3");
    }

    @Test
    void whenRedisIsDownTheLimitsFailOpen() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new RedisConnectionFailureException("MOCKED: Redis is down"));
        ChatRateLimiter openLimiter = new ChatRateLimiter(broken, properties());

        for (int i = 0; i < 50; i++) {
            assertThatCode(() -> openLimiter.acquire(1).close()).doesNotThrowAnyException();
        }
    }

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.AgentServer("http://x", "t", Duration.ofSeconds(2), Duration.ofSeconds(150), 262_144, 5,
                        Duration.ofSeconds(20)),
                null, null, new AppProperties.RateLimit(20, Duration.ofMinutes(10), 3));
    }
}
