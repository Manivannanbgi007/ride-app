package com.fooddelivery.chatservice.config;

import com.fooddelivery.chatservice.client.ServiceTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every RestClient this service builds (from Spring's RestClient.Builder) sends the shared service token on calls
 * of another service's internal API: those APIs let nothing else in (ServiceTokenAuthorizationManager there).
 */
@Configuration
public class InternalCallsConfig {

    @Bean
    public RestClientCustomizer serviceTokenOnInternalCalls(
            @Value("${app.security.internal-service-token}") String internalServiceToken) {
        return builder -> builder.requestInterceptor(new ServiceTokenInterceptor(internalServiceToken));
    }
}
