package com.fooddelivery.aimcpserver.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, JwtProperties.class})
public class ApplicationConfig {

    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.timezone());
    }

    /** One virtual thread per agent run / tool call: they mostly wait for the model or the gateway. */
    @Bean(destroyMethod = "close")
    public ExecutorService agentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
