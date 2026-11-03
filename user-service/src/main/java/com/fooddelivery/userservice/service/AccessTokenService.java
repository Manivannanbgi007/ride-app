package com.fooddelivery.userservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Creates access tokens: short-lived JWTs signed with RS256 that say who the caller is ("sub" = user id) and what
 * they are ("role"). Services trust a token only after checking its signature, issuer, audience and expiry.
 * A token carries no personal data (no name, email or phone).
 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final AppProperties appProperties;
    private final Clock clock;

    /** A signed access token and when it stops being valid. */
    public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {

        @Override
        public String toString() {
            return "AccessToken[value=****, expiresAt=" + expiresAt + "]";
        }
    }

    public AccessToken issue(User user) {
        AppProperties.Jwt settings = appProperties.jwt();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(settings.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .audience(List.of(settings.audience()))
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt, settings.accessTokenTtl().toSeconds());
    }
}
