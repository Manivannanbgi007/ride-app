package com.fooddelivery.userservice.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The RSA key pair that signs access tokens.
 * <p>
 * Only user-service has the private key. Every other service checks a token's signature with the public key, which
 * user-service publishes at {@code /.well-known/jwks.json}. So no other service can create tokens.
 * <p>
 * The key comes from {@code JWT_PRIVATE_KEY} (base64 of a PKCS#8 RSA key, 2048 bits or more). The key id ("kid") is
 * computed from the key itself, so it never needs configuring and changes automatically when the key changes.
 */
@Slf4j
@Configuration
public class JwtKeyConfig {

    static final int MINIMUM_KEY_BITS = 2048;

    @Bean
    public RSAKey jwtSigningKey(AppProperties appProperties) {
        AppProperties.Jwt jwt = appProperties.jwt();
        if (jwt.privateKey() != null && !jwt.privateKey().isBlank()) {
            RSAKey key = fromPrivateKey(jwt.privateKey());
            log.info("JWT signing key loaded. kid={}", key.getKeyID());
            return key;
        }
        if (!jwt.allowEphemeralKey()) {
            throw new IllegalStateException("No JWT signing key: set JWT_PRIVATE_KEY (base64 PKCS#8 RSA key, "
                    + "2048 bits or more). See README.md, 'Security (JWT)'.");
        }
        RSAKey key = generateEphemeral();
        log.warn("No JWT_PRIVATE_KEY configured: using a temporary signing key (development only). kid={}. "
                + "Access tokens stop being accepted when user-service restarts (the web app then refreshes them); "
                + "set JWT_PRIVATE_KEY in .env to keep one key. Never run more than one user-service like this.",
                key.getKeyID());
        return key;
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /**
     * Checks the access tokens sent to this service's own APIs with its own public key: the same checks every other
     * service makes (signature, expiry, issuer, audience; SecurityConfig), without asking itself for the key.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, AppProperties appProperties) {
        RSAPublicKey publicKey;
        try {
            publicKey = jwtSigningKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("The public key of the JWT signing key cannot be read", e);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        AppProperties.Jwt settings = appProperties.jwt();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audiences -> audiences != null && audiences.contains(settings.audience()))));
        return decoder;
    }

    /** Reads a base64 PKCS#8 RSA private key (PEM header and footer lines and line breaks are allowed). */
    static RSAKey fromPrivateKey(String encodedKey) {
        String base64 = encodedKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            if (!(rsa.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
                    instanceof RSAPrivateCrtKey privateKey)) {
                throw new IllegalStateException("JWT_PRIVATE_KEY must be a complete RSA private key (PKCS#8)");
            }
            if (privateKey.getModulus().bitLength() < MINIMUM_KEY_BITS) {
                throw new IllegalStateException("JWT_PRIVATE_KEY is too short: use an RSA key of at least "
                        + MINIMUM_KEY_BITS + " bits");
            }
            // The public half is part of the private key's data: no second setting needed
            RSAPublicKey publicKey = (RSAPublicKey) rsa.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return signingKey(publicKey, privateKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // The message never contains the key itself
            throw new IllegalStateException("JWT_PRIVATE_KEY is not a valid base64 PKCS#8 RSA private key", e);
        }
    }

    static RSAKey generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MINIMUM_KEY_BITS);
            KeyPair pair = generator.generateKeyPair();
            return signingKey((RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate a temporary JWT signing key", e);
        }
    }

    private static RSAKey signingKey(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) {
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot compute the JWT key id", e);
        }
    }
}
