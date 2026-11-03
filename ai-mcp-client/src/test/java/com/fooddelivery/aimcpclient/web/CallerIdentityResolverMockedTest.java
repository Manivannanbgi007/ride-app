package com.fooddelivery.aimcpclient.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import com.fooddelivery.aimcpclient.MutableClock;
import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.exception.AuthenticationFailedException;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import com.fooddelivery.aimcpclient.identity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller of a verified access token (Spring Security has checked its signature, expiry, issuer and audience before
 * this class sees it): the user id and role come from the token only, and it must last for a whole answer.
 */
class CallerIdentityResolverMockedTest {

    private final MutableClock clock = new MutableClock();
    private final CallerIdentityResolver resolver = new CallerIdentityResolver(clock,
            new AppProperties(null, new AppProperties.Identity(Duration.ofSeconds(180)), null, null));

    private Jwt token(String subject, String role, Instant expiresAt) {
        Jwt.Builder token = Jwt.withTokenValue("the-token").header("alg", "RS256").subject(subject)
                .issuedAt(clock.instant()).expiresAt(expiresAt);
        if (role != null) {
            token.claim("role", role);
        }
        return token.build();
    }

    private Jwt token(String subject, String role) {
        return token(subject, role, clock.instant().plusSeconds(900));
    }

    @Test
    void theCallerIsTheTokensUserAndRoleWithTheTokenToForward() {
        CallerIdentity caller = resolver.resolve(token("7", "DELIVERY_PARTNER"));

        assertThat(caller.userId()).isEqualTo(7);
        assertThat(caller.role()).isEqualTo(Role.DELIVERY_PARTNER);
        assertThat(caller.accessToken()).isEqualTo("the-token");
        assertThat(caller.toString()).doesNotContain("the-token");
    }

    @Test
    void noTokenIs401() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void aSubjectThatIsNotAUserIdIs401() {
        for (String subject : new String[] {"abc", "0", "-3", "1.5"}) {
            assertThatThrownBy(() -> resolver.resolve(token(subject, "CUSTOMER")))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
    }

    @Test
    void aMissingOrUnknownRoleIs401() {
        assertThatThrownBy(() -> resolver.resolve(token("7", null))).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> resolver.resolve(token("7", "SUPERUSER")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void aTokenThatWouldExpireDuringAnAnswerIs401() {
        Instant soon = clock.instant().plusSeconds(179);

        assertThatThrownBy(() -> resolver.resolve(token("7", "CUSTOMER", soon)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("renewing");
        assertThat(resolver.resolve(token("7", "CUSTOMER", clock.instant().plusSeconds(181))).userId()).isEqualTo(7);
    }

    @Test
    void theRoleClaimIsReadWhateverItsCase() {
        assertThat(resolver.resolve(token("9", "restaurant_admin")).role()).isEqualTo(Role.RESTAURANT_ADMIN);
    }
}
