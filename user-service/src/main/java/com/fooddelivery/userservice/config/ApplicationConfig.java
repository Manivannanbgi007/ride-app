package com.fooddelivery.userservice.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ApplicationConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Injected instead of calling Instant.now() directly, so tests can use a fixed time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
