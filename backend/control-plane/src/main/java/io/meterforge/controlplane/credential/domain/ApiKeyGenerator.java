package io.meterforge.controlplane.credential.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class ApiKeyGenerator {

    private static final String ALPHANUMERIC = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final String SECRET_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String pepper;

    public ApiKeyGenerator(@Value("${meterforge.api-key.pepper:dev-secret-pepper-change-in-production-12345678}") String pepper) {
        this.pepper = pepper;
    }

    public record GeneratedApiKey(
            String fullKey,
            String publicId,
            String secret,
            String secretHmac,
            String displayPrefix,
            String displayLastFour,
            String environment
    ) {}

    public GeneratedApiKey generateKey(String environment) {
        String env = (environment != null && !environment.isBlank()) ? environment.trim().toLowerCase() : "dev";
        String publicId = randomString(ALPHANUMERIC, 12);
        String secret = randomString(SECRET_CHARS, 40);

        String fullKey = "mf_" + env + "_" + publicId + "_" + secret;
        String hmac = computeHmac(fullKey);

        String displayPrefix = fullKey.substring(0, Math.min(12, fullKey.length()));
        String displayLastFour = secret.substring(secret.length() - 4);

        return new GeneratedApiKey(
                fullKey,
                publicId,
                secret,
                hmac,
                displayPrefix,
                displayLastFour,
                env
        );
    }

    public String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for API key", e);
        }
    }

    public boolean constantTimeEquals(String hmacA, String hmacB) {
        if (hmacA == null || hmacB == null) return false;
        return MessageDigest.isEqual(
                hmacA.getBytes(StandardCharsets.UTF_8),
                hmacB.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String randomString(String characters, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RANDOM.nextInt(characters.length());
            sb.append(characters.charAt(idx));
        }
        return sb.toString();
    }
}
