package com.fooddelivery.catalogservice.config;

import com.fooddelivery.catalogservice.web.SecurityProblemHandlers;
import com.fooddelivery.catalogservice.web.ServiceTokenAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call which API of this service (phase 18). The caller is the access token's user: Spring Security checks the
 * token (signature with user-service's public key, expiry, issuer, audience; spring.security.oauth2.resourceserver.jwt
 * in application.yml) and its "role" claim becomes the caller's role. Rules from the most specific to the least;
 * anything not listed needs a signed-in caller. What belongs to whom is checked by the services, as before.
 * <p>
 * Internal APIs are for the other services only: they need the shared X-Service-Token instead of a user's token.
 * Stateless (no session, no cookie), so there is nothing for a cross-site request to use: CSRF protection is off.
 */
@Configuration
public class SecurityConfig {

    /** Health and metrics (Docker and Prometheus) and the API docs need no token */
    static final String[] OPEN = {"/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandlers problems,
                                                   ServiceTokenAuthorizationManager serviceToken) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(OPEN).permitAll()
                        // The other actuator endpoints (circuit breakers, retries...): administrators only
                        .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/internal/**").access(serviceToken)
                        // Restaurant owners manage the menus of their own restaurants
                        .requestMatchers("/api/v1/restaurants/mine/**").hasRole("RESTAURANT_ADMIN")
                        // Anyone may read the menus and the categories
                        .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/*/menu", "/api/v1/categories").permitAll()
                        // Platform admins monitor and manage everything of this service
                        .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }
}
