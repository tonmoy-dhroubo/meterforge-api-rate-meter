package io.meterforge.controlplane;

import io.meterforge.controlplane.credential.domain.ApiKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHmacConsistencyTests {

    private final String pepper = "test_server_secret_pepper_1234567890_abcdef";
    private final ApiKeyGenerator generator = new ApiKeyGenerator(pepper);

    @Test
    @DisplayName("Generated API key HMAC matches independent HMAC computation with same pepper")
    void testHmacGenerationMatchesGatewayVerifier() throws Exception {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generateKey("prod");

        assertThat(generated.fullKey()).startsWith("mf_prod_");
        assertThat(generated.publicId()).isNotBlank();
        assertThat(generated.secret()).isNotBlank();
        assertThat(generated.secretHmac()).isNotBlank();

        // Independent Gateway verification logic
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] expectedBytes = mac.doFinal(generated.fullKey().getBytes(StandardCharsets.UTF_8));
        String gatewayHmac = HexFormat.of().formatHex(expectedBytes);

        assertThat(generated.secretHmac()).isEqualTo(gatewayHmac);
        assertThat(generator.constantTimeEquals(generated.secretHmac(), gatewayHmac)).isTrue();
    }

    @Test
    @DisplayName("Tampered API key fails HMAC validation")
    void testTamperedKeyFailsValidation() {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generateKey("dev");

        String tamperedKey = generated.fullKey() + "x";
        String tamperedHmac = generator.computeHmac(tamperedKey);

        assertThat(generator.constantTimeEquals(generated.secretHmac(), tamperedHmac)).isFalse();
        assertThat(MessageDigest.isEqual(
                generated.secretHmac().getBytes(StandardCharsets.UTF_8),
                tamperedHmac.getBytes(StandardCharsets.UTF_8)
        )).isFalse();
    }

    @Test
    @DisplayName("Different pepper produces distinct HMAC for identical key")
    void testDifferentPepperProducesDistinctHmac() {
        ApiKeyGenerator otherGenerator = new ApiKeyGenerator("different_pepper_value");
        ApiKeyGenerator.GeneratedApiKey generated = generator.generateKey("dev");

        String otherHmac = otherGenerator.computeHmac(generated.fullKey());
        assertThat(otherHmac).isNotEqualTo(generated.secretHmac());
    }

    @Test
    @DisplayName("Seeded demo key HMAC matches V4 migration and unified default pepper")
    void testSeededDemoKeyMatchesDefaultPepper() {
        ApiKeyGenerator defaultGenerator = new ApiKeyGenerator("dev-secret-pepper-change-in-production-12345678");
        String demoKey = "mf_dev_nsdemo123456_seedednorthstardemosecretkey9999";
        String expectedHmac = "53a6a7444edad27f3b2165d6e2b7275ea954dc86b6d41884b3df2adab0bad695";

        assertThat(defaultGenerator.computeHmac(demoKey)).isEqualTo(expectedHmac);
    }
}
