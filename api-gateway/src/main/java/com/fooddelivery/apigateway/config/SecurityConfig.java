package com.fooddelivery.apigateway.config;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.JwkSetUriReactiveJwtDecoderBuilderCustomizer;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * The platform's first line of defence (phase 18). Every request that carries an access token
 * ({@code Authorization: Bearer}) must carry a valid one: signed by user-service (public key from its JWKS), not
 * expired, issued by and for this platform (spring.security.oauth2.resourceserver.jwt in application.yml). Otherwise
 * 401 here, before any service is called.
 * <ul>
 *   <li>{@code /api/v1/admin/**}: platform administrators only (403 for another role, 401 without a token).</li>
 *   <li>{@code /api/v1/internal/**}: never from outside (403); services call each other directly.</li>
 *   <li>{@code /actuator/**}: health, info and prometheus are open (monitoring); the others (the gateway's routes,
 *       which name every service's address) for platform administrators only.</li>
 *   <li>Everything else passes on: public pages need no token, and each service decides what its APIs need (each
 *       service checks the token again).</li>
 * </ul>
 * Headers only the platform may send (X-User-*, X-Service-Token) are removed from every request (IdentityHeadersFilter).
 * CORS is answered here (from the gateway's globalcors settings), so even a 401 carries the CORS headers and the web app
 * can read it. Stateless: no session and no cookie for the API, so CSRF protection is not needed.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** Reading user-service's public key: it must not hold requests up for long when user-service is slow */
    private static final int JWKS_CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final Duration JWKS_RESPONSE_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         GatewaySecurityProblemHandlers problems,
                                                         CorsConfigurationSource corsConfigurationSource) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        // CORS preflight: the browser asks before sending a token
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/api/v1/internal/**").denyAll()
                        .pathMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
                        .anyExchange().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems)
                        .authenticationFailureHandler(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .build();
    }

    /** The gateway's own CORS settings (spring.cloud.gateway.server.webflux.globalcors), applied by Spring Security. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(GlobalCorsProperties globalCors) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        globalCors.getCorsConfigurations().forEach(source::registerCorsConfiguration);
        return source;
    }

    /** Short timeouts for reading the public key (it is read once and kept; again only for an unknown key id). */
    @Bean
    public JwkSetUriReactiveJwtDecoderBuilderCustomizer jwksTimeouts() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, JWKS_CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(JWKS_RESPONSE_TIMEOUT);
        WebClient webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
        return builder -> builder.webClient(webClient);
    }
}
