package com.fooddelivery.aimcpclient.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    /** Injected instead of calling Instant.now() directly, so tests can use a fixed time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
