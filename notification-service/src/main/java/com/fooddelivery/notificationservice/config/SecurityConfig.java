package com.fooddelivery.notificationservice.config;

import com.fooddelivery.notificationservice.web.SecurityProblemHandlers;
import com.fooddelivery.notificationservice.web.ServiceTokenAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call which API of this service (phase 18). The caller is the access token's user: Spring Security checks the
 * token (signature with user-service's public key, expiry, issuer, audience; spring.security.oauth2.resourceserver.jwt
 * in application.yml) and its "role" claim becomes the caller's role. Rules from the most specific to the least;
 * anything not listed needs a signed-in caller. What belongs to whom is checked by the services, as before.
 * <p>
 * Internal APIs are for the other services only: they need the shared X-Service-Token instead of a user's token.
 * A browser cannot set headers on a WebSocket: the live connection (/ws/**) may carry the token as
 * {@code ?access_token=}, and only there.
 * Stateless (no session, no cookie), so there is nothing for a cross-site request to use: CSRF protection is off.
 */
@Configuration
public class SecurityConfig {

    /** Health and metrics (Docker and Prometheus) and the API docs need no token */
    static final String[] OPEN = {"/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    private static final String LIVE_PATHS = "/ws/";

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
                        // Platform admins monitor and manage everything of this service
                        .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(headerOrLiveQuery())
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }

    /** The token of the Authorization header, or (for the live connection only) of the access_token query parameter. */
    private static BearerTokenResolver headerOrLiveQuery() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver headerOrQuery = new DefaultBearerTokenResolver();
        headerOrQuery.setAllowUriQueryParameter(true);
        return request -> request.getRequestURI().startsWith(LIVE_PATHS)
                ? headerOrQuery.resolve(request)
                : header.resolve(request);
    }
}
