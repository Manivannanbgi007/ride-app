package com.fooddelivery.adminservice.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    /** Injected instead of calling Instant.now() directly, so tests can use a fixed time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** One virtual thread per remote call: waiting for other services costs no platform thread. */
    @Bean(destroyMethod = "close")
    public ExecutorService remoteCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
