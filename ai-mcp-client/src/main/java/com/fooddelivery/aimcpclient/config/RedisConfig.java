package com.fooddelivery.aimcpclient.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis holds conversations and counters, but the chat must still answer without it: commands fail at once while
 * the connection is lost (instead of queueing until the timeout) and the client reconnects in the background.
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
