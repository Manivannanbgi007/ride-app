package com.fooddelivery.aimcpserver.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The checker of callers' access tokens: signed by user-service (RS256, public key from its JWKS), not expired, and
 * issued by and for this platform. The same checks Spring Security makes in ai-mcp-client, made again here because
 * this service acts with the token.
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder callerTokenDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audiences -> audiences != null && audiences.contains(properties.audience()))));
        return decoder;
    }
}
