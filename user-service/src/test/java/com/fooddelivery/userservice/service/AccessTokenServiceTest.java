package com.fooddelivery.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.config.JwtKeyConfig;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.service.AccessTokenService.AccessToken;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class AccessTokenServiceTest {

    // Real time: the decoder below checks expiry against the clock of this machine
    private static final Instant NOW = Instant.now();

    private RSAKey signingKey;
    private AccessTokenService accessTokenService;

    @BeforeEach
    void setUp() {
        AppProperties.Jwt jwt = new AppProperties.Jwt("https://auth.test", "test-api", Duration.ofMinutes(15), null, true);
        AppProperties properties = new AppProperties("Asia/Kolkata", null, null, jwt, null);
        JwtKeyConfig keyConfig = new JwtKeyConfig();
        signingKey = keyConfig.jwtSigningKey(properties);
        accessTokenService = new AccessTokenService(keyConfig.jwtEncoder(signingKey), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theTokenIsAnRs256JwtWithTheUserAndRoleThatTheIssuersPublicKeyVerifies() throws Exception {
        AccessToken token = accessTokenService.issue(owner());

        Jwt jwt = decoderFor("https://auth.test").decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("RESTAURANT_ADMIN");
        assertThat(jwt.getAudience()).containsExactly("test-api");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW.truncatedTo(ChronoUnit.SECONDS));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofMinutes(15)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(token.expiresInSeconds()).isEqualTo(900);
        // The header names the key, so services pick the right public key
        SignedJWT parsed = SignedJWT.parse(token.value());
        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(signingKey.getKeyID());
        // No personal data in a token anyone can decode
        assertThat(parsed.getPayload().toString()).doesNotContain("@");
    }

    @Test
    void everyTokenHasItsOwnId() {
        assertThat(accessTokenService.issue(owner()).value()).isNotEqualTo(accessTokenService.issue(owner()).value());
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() throws JOSEException {
        String token = accessTokenService.issue(owner()).value();
        NimbusJwtDecoder otherIssuer = decoderFor("https://someone-else.test");

        assertThatThrownBy(() -> otherIssuer.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws JOSEException {
        String token = accessTokenService.issue(owner()).value();
        RSAKey otherKey = new JwtKeyConfig().jwtSigningKey(new AppProperties("Asia/Kolkata", null, null,
                new AppProperties.Jwt("https://auth.test", "test-api", Duration.ofMinutes(15), null, true), null));
        NimbusJwtDecoder otherKeyDecoder = NimbusJwtDecoder.withPublicKey(otherKey.toRSAPublicKey()).build();

        assertThatThrownBy(() -> otherKeyDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void toStringHidesTheToken() {
        AccessToken token = accessTokenService.issue(owner());

        assertThat(token.toString()).doesNotContain(token.value());
    }

    private NimbusJwtDecoder decoderFor(String issuer) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static User owner() {
        User user = new User();
        user.setId(42L);
        user.setEmail("owner@example.com");
        user.setRole(Role.RESTAURANT_ADMIN);
        return user;
    }
}
