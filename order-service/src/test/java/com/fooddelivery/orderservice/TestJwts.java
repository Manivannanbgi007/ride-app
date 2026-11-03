package com.fooddelivery.orderservice;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access tokens for tests, signed the way user-service signs them (RS256, the platform's issuer and audience), and a
 * local server that publishes the public key at /.well-known/jwks.json: the application checks them exactly as in
 * production. Point spring.security.oauth2.resourceserver.jwt.jwk-set-uri at {@link #jwksUri()}.
 */
public final class TestJwts {

    public static final String ISSUER = "https://auth.fooddelivery.local";
    public static final String AUDIENCE = "food-delivery-api";

    private static final RSAKey KEY = newKey();
    /** A key the application does not know: its tokens must be refused */
    private static final RSAKey UNKNOWN_KEY = newKey();
    private static final String JWKS_URI = publish(KEY);

    private TestJwts() {
    }

    public static String jwksUri() {
        return JWKS_URI;
    }

    /** A valid token of the user, for 15 minutes. */
    public static String token(long userId, String role) {
        return token(userId, role, claims -> { });
    }

    /** "Bearer <a valid token of the user>", as an Authorization header. */
    public static String bearer(long userId, String role) {
        return "Bearer " + token(userId, role);
    }

    /** A token of the user with some claims changed (an expiry, another audience...). */
    public static String token(long userId, String role, Consumer<JwtClaimsSet.Builder> change) {
        return sign(KEY, claims(userId, role, change));
    }

    /** A token that looks right but is signed with a key user-service never had. */
    public static String tokenSignedByAnotherKey(long userId, String role) {
        return sign(UNKNOWN_KEY, claims(userId, role, claims -> { }));
    }

    private static JwtClaimsSet claims(long userId, String role, Consumer<JwtClaimsSet.Builder> change) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .id(UUID.randomUUID().toString());
        change.accept(claims);
        return claims.build();
    }

    private static String sign(RSAKey key, JwtClaimsSet claims) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String publish(RSAKey key) {
        byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
