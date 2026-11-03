package com.fooddelivery.restaurantservice.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis is only a cache here: when it is down, requests must not wait for it. Commands fail at once while the
 * connection is lost (instead of queueing until the timeout), and the client keeps reconnecting in the background.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisIsDown() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }
}
