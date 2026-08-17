package io.meterforge.worker.configprojection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.CredentialConfigurationChangedV1;
import io.meterforge.contracts.event.ProductConfigurationChangedV1;
import io.meterforge.contracts.event.RouteConfigurationChangedV1;
import io.meterforge.contracts.event.SubscriptionConfigurationChangedV1;
import io.meterforge.contracts.projection.CredentialProjection;
import io.meterforge.contracts.projection.ProductProjection;
import io.meterforge.contracts.projection.RouteProjection;
import io.meterforge.contracts.projection.SubscriptionProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConfigProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConfigProjectionConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ConfigProjectionConsumer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${meterforge.kafka.topics.config:meterforge.config.v1}",
            groupId = "${spring.kafka.consumer.group-id:meterforge-worker-config-projection}"
    )
    public void consumeConfigEvent(String message) {
        try {
            ConfigEventEnvelope<Object> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<ConfigEventEnvelope<Object>>() {}
            );

            String aggregateType = envelope.aggregateType();
            String aggregateId = envelope.aggregateId().toString();
            long incomingVersion = envelope.aggregateVersion();

            String versionKey = "rf:v1:cfg:version:" + aggregateType + ":" + aggregateId;
            String currentVersionStr = redisTemplate.opsForValue().get(versionKey);
            long currentVersion = currentVersionStr != null ? Long.parseLong(currentVersionStr) : -1L;

            if (incomingVersion <= currentVersion) {
                log.debug("Skipping outdated/duplicate config event for {}:{} (incoming={}, current={})",
                        aggregateType, aggregateId, incomingVersion, currentVersion);
                return;
            }

            String eventType = envelope.eventType();
            Object payload = envelope.payload();

            if ("CredentialConfigurationChangedV1".equals(eventType) || "ApiCredential".equals(aggregateType)) {
                CredentialConfigurationChangedV1 credEvent = objectMapper.convertValue(payload, CredentialConfigurationChangedV1.class);
                CredentialProjection projection = new CredentialProjection(
                        credEvent.credentialId(),
                        credEvent.workspaceId(),
                        credEvent.applicationId(),
                        credEvent.publicId(),
                        credEvent.secretHmac(),
                        credEvent.environment(),
                        credEvent.status(),
                        credEvent.expiresAt(),
                        credEvent.revokedAt(),
                        credEvent.version()
                );
                String credKey = "rf:v1:cfg:credential:" + credEvent.publicId();
                redisTemplate.opsForValue().set(credKey, objectMapper.writeValueAsString(projection));
                log.info("Projected credential {} (publicId={}) to Redis key {}", credEvent.credentialId(), credEvent.publicId(), credKey);
            } else if ("ProductConfigurationChangedV1".equals(eventType) || "ApiProduct".equals(aggregateType)) {
                ProductConfigurationChangedV1 prodEvent = objectMapper.convertValue(payload, ProductConfigurationChangedV1.class);
                String prodKey = "rf:v1:cfg:product:" + prodEvent.productId();

                // Preserve existing routes if present
                List<RouteProjection> existingRoutes = new ArrayList<>();
                String existingProdJson = redisTemplate.opsForValue().get(prodKey);
                if (existingProdJson != null) {
                    try {
                        ProductProjection existingProd = objectMapper.readValue(existingProdJson, ProductProjection.class);
                        if (existingProd.routes() != null) {
                            existingRoutes.addAll(existingProd.routes());
                        }
                    } catch (Exception ignored) {}
                }

                ProductProjection projection = new ProductProjection(
                        prodEvent.productId(),
                        prodEvent.workspaceId(),
                        prodEvent.name(),
                        prodEvent.slug(),
                        prodEvent.upstreamBaseUrl(),
                        prodEvent.gatewayBasePath(),
                        prodEvent.status(),
                        existingRoutes,
                        prodEvent.version()
                );
                redisTemplate.opsForValue().set(prodKey, objectMapper.writeValueAsString(projection));
                log.info("Projected product {} to Redis key {}", prodEvent.productId(), prodKey);
            } else if ("RouteConfigurationChangedV1".equals(eventType) || "ApiRoute".equals(aggregateType)) {
                RouteConfigurationChangedV1 routeEvent = objectMapper.convertValue(payload, RouteConfigurationChangedV1.class);
                String prodKey = "rf:v1:cfg:product:" + routeEvent.productId();
                String existingProdJson = redisTemplate.opsForValue().get(prodKey);
                if (existingProdJson != null) {
                    ProductProjection existingProd = objectMapper.readValue(existingProdJson, ProductProjection.class);
                    List<RouteProjection> routes = new ArrayList<>();
                    if (existingProd.routes() != null) {
                        for (RouteProjection r : existingProd.routes()) {
                            if (!r.routeId().equals(routeEvent.routeId())) {
                                routes.add(r);
                            }
                        }
                    }
                    routes.add(new RouteProjection(
                            routeEvent.routeId(),
                            routeEvent.httpMethod(),
                            routeEvent.pathPattern(),
                            routeEvent.upstreamPath(),
                            routeEvent.costUnits(),
                            routeEvent.priority(),
                            routeEvent.status(),
                            routeEvent.version()
                    ));

                    ProductProjection updated = new ProductProjection(
                            existingProd.productId(),
                            existingProd.workspaceId(),
                            existingProd.name(),
                            existingProd.slug(),
                            existingProd.upstreamBaseUrl(),
                            existingProd.gatewayBasePath(),
                            existingProd.status(),
                            routes,
                            existingProd.version()
                    );
                    redisTemplate.opsForValue().set(prodKey, objectMapper.writeValueAsString(updated));
                    log.info("Projected route {} into product {} in Redis", routeEvent.routeId(), routeEvent.productId());
                }
            } else if ("SubscriptionConfigurationChangedV1".equals(eventType) || "Subscription".equals(aggregateType)) {
                SubscriptionConfigurationChangedV1 subEvent = objectMapper.convertValue(payload, SubscriptionConfigurationChangedV1.class);
                String subKey = "rf:v1:cfg:subscription:" + subEvent.subscriptionId();
                SubscriptionProjection projection = new SubscriptionProjection(
                        subEvent.subscriptionId(),
                        subEvent.workspaceId(),
                        subEvent.applicationId(),
                        subEvent.productId(),
                        subEvent.planId(),
                        subEvent.status(),
                        List.of(),
                        subEvent.effectiveFrom(),
                        subEvent.effectiveTo(),
                        subEvent.version()
                );
                redisTemplate.opsForValue().set(subKey, objectMapper.writeValueAsString(projection));
                log.info("Projected subscription {} to Redis key {}", subEvent.subscriptionId(), subKey);
            }

            // Atomically update version key
            redisTemplate.opsForValue().set(versionKey, String.valueOf(incomingVersion));
        } catch (Exception e) {
            log.error("Failed to process config projection event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
