package io.meterforge.gateway.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.projection.CredentialProjection;
import io.meterforge.gateway.config.MeterForgeGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class ApiKeyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticator.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterForgeGatewayProperties properties;

    // Small bounded L1 cache for credential projections (5 seconds TTL)
    private final Cache<String, CredentialProjection> credentialCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();

    public ApiKeyAuthenticator(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterForgeGatewayProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void clearCache() {
        credentialCache.invalidateAll();
    }

    public record ParsedApiKey(String rawKey, String environment, String publicId, String secret) {}

    public Mono<CredentialProjection> authenticate(ServerHttpRequest request) {
        String rawKey = request.getHeaders().getFirst(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            return Mono.empty();
        }

        ParsedApiKey parsed = parseKey(rawKey.trim());
        if (parsed == null) {
            return Mono.empty();
        }

        return loadCredentialProjection(parsed.publicId())
                .filter(projection -> verifyCredential(parsed, projection));
    }

    public ParsedApiKey parseKey(String rawKey) {
        if (!rawKey.startsWith("mf_")) {
            return null;
        }
        String[] parts = rawKey.split("_", 4);
        if (parts.length < 4) {
            return null;
        }
        return new ParsedApiKey(rawKey, parts[1], parts[2], parts[3]);
    }

    private Mono<CredentialProjection> loadCredentialProjection(String publicId) {
        CredentialProjection cached = credentialCache.getIfPresent(publicId);
        if (cached != null) {
            return Mono.just(cached);
        }

        String key = "rf:v1:cfg:credential:" + publicId;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        CredentialProjection projection = objectMapper.readValue(json, CredentialProjection.class);
                        credentialCache.put(publicId, projection);
                        return Mono.just(projection);
                    } catch (Exception e) {
                        log.error("Failed to parse credential projection for publicId {}: {}", publicId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    private boolean verifyCredential(ParsedApiKey parsed, CredentialProjection projection) {
        if (projection.status() != ResourceStatus.ACTIVE) {
            return false;
        }

        Instant now = Instant.now();
        if (projection.expiresAt() != null && now.isAfter(projection.expiresAt())) {
            return false;
        }
        if (projection.revokedAt() != null && now.isAfter(projection.revokedAt())) {
            return false;
        }

        String computedHmac = computeHmacSha256(properties.getApiKeyPepper(), parsed.rawKey());
        return MessageDigest.isEqual(
                computedHmac.getBytes(StandardCharsets.UTF_8),
                projection.secretHmac().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String computeHmacSha256(String pepper, String rawKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }
}
