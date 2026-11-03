package com.fooddelivery.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    @Test
    void loadsAConfiguredKeyAndDerivesTheKeyIdFromIt() throws Exception {
        String encoded = pkcs8(2048);

        RSAKey key = config.jwtSigningKey(properties(encoded, false));
        RSAKey again = config.jwtSigningKey(properties(encoded, false));

        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getKeyID()).isNotBlank().isEqualTo(again.getKeyID());
        assertThat(key.getAlgorithm().getName()).isEqualTo("RS256");
    }

    @Test
    void acceptsAKeyWithPemArmourAndLineBreaks() throws Exception {
        String base64 = pkcs8(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64.replaceAll("(.{64})", "$1\n") + "\n-----END PRIVATE KEY-----";

        assertThat(config.jwtSigningKey(properties(pem, false)).getKeyID())
                .isEqualTo(config.jwtSigningKey(properties(base64, false)).getKeyID());
    }

    @Test
    void refusesToStartWithoutAKeyUnlessATemporaryOneIsAllowed() {
        AppProperties withoutKey = properties(null, false);

        assertThatThrownBy(() -> config.jwtSigningKey(withoutKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    void generatesATemporaryKeyWhenAllowed() {
        RSAKey first = config.jwtSigningKey(properties("  ", true));
        RSAKey second = config.jwtSigningKey(properties(null, true));

        assertThat(first.isPrivate()).isTrue();
        assertThat(first.size()).isGreaterThanOrEqualTo(2048);
        // A new key gets a new key id, so services fetch the new public key instead of rejecting good tokens forever
        assertThat(first.getKeyID()).isNotEqualTo(second.getKeyID());
    }

    @Test
    void rejectsShortKeys() throws Exception {
        AppProperties weak = properties(pkcs8(1024), false);

        assertThatThrownBy(() -> config.jwtSigningKey(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void rejectsSomethingThatIsNotAKeyWithoutRepeatingIt() {
        AppProperties garbage = properties("not-a-key-at-all", false);

        assertThatThrownBy(() -> config.jwtSigningKey(garbage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-a-key-at-all");
    }

    @Test
    void settingsToStringHidesThePrivateKey() {
        assertThat(properties("very-secret-key", false).jwt().toString()).doesNotContain("very-secret-key");
    }

    private static AppProperties properties(String privateKey, boolean allowEphemeralKey) {
        AppProperties.Jwt jwt = new AppProperties.Jwt("https://auth.test", "test-api", Duration.ofMinutes(15),
                privateKey, allowEphemeralKey);
        return new AppProperties("Asia/Kolkata", null, null, jwt, null);
    }

    private static String pkcs8(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    }
}
