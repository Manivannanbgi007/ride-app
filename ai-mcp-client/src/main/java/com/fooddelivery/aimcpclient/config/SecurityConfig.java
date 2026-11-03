package com.fooddelivery.aimcpclient.config;

import com.fooddelivery.aimcpclient.web.SecurityProblemHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call this service. Every assistant call needs a valid access token from user-service
 * ({@code Authorization: Bearer}); Spring Security checks its signature with user-service's public key (JWKS), its
 * expiry, issuer and audience (spring.security.oauth2.resourceserver.jwt in application.yml). Every role has the
 * assistant: what each role may do is decided per tool by ai-mcp-server and, in the end, by the services themselves.
 * <p>
 * Stateless (no session, no cookie), so there is nothing for a cross-site request to ride on: CSRF protection is off.
 * Anything not listed is refused.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandlers problems) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // Errors are rendered on an internal dispatch: it must not ask for a token again
                        .requestMatchers("/error").permitAll()
                        // Whether the assistant can answer at all: shown before signing in
                        .requestMatchers(HttpMethod.GET, "/api/v1/assistant/status").permitAll()
                        .requestMatchers("/api/v1/assistant/**").authenticated()
                        .anyRequest().denyAll())
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
