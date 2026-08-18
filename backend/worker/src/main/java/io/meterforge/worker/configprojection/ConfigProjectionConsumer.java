package io.meterforge.worker.configprojection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.CredentialConfigurationChangedV1;
import io.meterforge.contracts.event.LimitPolicyDto;
import io.meterforge.contracts.event.PlanConfigurationChangedV1;
import io.meterforge.contracts.event.ProductConfigurationChangedV1;
import io.meterforge.contracts.event.RouteConfigurationChangedV1;
import io.meterforge.contracts.event.SubscriptionConfigurationChangedV1;
import io.meterforge.contracts.projection.CredentialProjection;
import io.meterforge.contracts.projection.PlanProjection;
import io.meterforge.contracts.projection.PolicyProjection;
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
    public static final String ACTIVE_PRODUCTS_SET_KEY = "rf:v1:cfg:products";

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

            if ("CredentialConfigurationChangedV1".equals(eventType) || "ApiCredential".equalsIgnoreCase(aggregateType) || "CREDENTIAL".equalsIgnoreCase(aggregateType)) {
                CredentialConfigurationChangedV1 credEvent = objectMapper.convertValue(payload, CredentialConfigurationChangedV1.class);
                CredentialProjection projection = new CredentialProjection(
                        credEvent.credentialId(),
                        credEvent.workspaceId(),
                        credEvent.consumerId(),
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
                redisTemplate.opsForValue().set(versionKey, String.valueOf(incomingVersion));
                log.info("Projected credential {} (publicId={}) to Redis key {}", credEvent.credentialId(), credEvent.publicId(), credKey);

            } else if ("ProductConfigurationChangedV1".equals(eventType) || "ApiProduct".equalsIgnoreCase(aggregateType) || "PRODUCT".equalsIgnoreCase(aggregateType)) {
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

                // Maintain active products index in Redis Set
                if (prodEvent.status() == ResourceStatus.ACTIVE) {
                    redisTemplate.opsForSet().add(ACTIVE_PRODUCTS_SET_KEY, prodEvent.productId().toString());
                } else {
                    redisTemplate.opsForSet().remove(ACTIVE_PRODUCTS_SET_KEY, prodEvent.productId().toString());
                }

                redisTemplate.opsForValue().set(versionKey, String.valueOf(incomingVersion));
                log.info("Projected product {} to Redis key {}", prodEvent.productId(), prodKey);

            } else if ("RouteConfigurationChangedV1".equals(eventType) || "ApiRoute".equalsIgnoreCase(aggregateType) || "ROUTE".equalsIgnoreCase(aggregateType)) {
                RouteConfigurationChangedV1 routeEvent = objectMapper.convertValue(payload, RouteConfigurationChangedV1.class);
                String prodKey = "rf:v1:cfg:product:" + routeEvent.productId();
                String existingProdJson = redisTemplate.opsForValue().get(prodKey);
                ProductProjection existingProd = null;

                if (existingProdJson != null) {
                    existingProd = objectMapper.readValue(existingProdJson, ProductProjection.class);
                }

                List<RouteProjection> routes = new ArrayList<>();
                if (existingProd != null && existingProd.routes() != null) {
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
                        routeEvent.productId(),
                        routeEvent.workspaceId(),
                        existingProd != null ? existingProd.name() : "",
                        existingProd != null ? existingProd.slug() : "",
                        existingProd != null ? existingProd.upstreamBaseUrl() : "",
                        existingProd != null ? existingProd.gatewayBasePath() : "",
                        existingProd != null ? existingProd.status() : ResourceStatus.ACTIVE,
                        routes,
                        existingProd != null ? existingProd.version() : 0
                );
                redisTemplate.opsForValue().set(prodKey, objectMapper.writeValueAsString(updated));
                redisTemplate.opsForValue().set(versionKey, String.valueOf(incomingVersion));
                log.info("Projected route {} into product {} in Redis", routeEvent.routeId(), routeEvent.productId());

            } else if ("PlanConfigurationChangedV1".equals(eventType) || "Plan".equalsIgnoreCase(aggregateType) || "PLAN".equalsIgnoreCase(aggregateType)) {
                PlanConfigurationChangedV1 planEvent = objectMapper.convertValue(payload, PlanConfigurationChangedV1.class);
                List<PolicyProjection> policyProjections = new ArrayList<>();
                if (planEvent.policies() != null) {
                    for (LimitPolicyDto dto : planEvent.policies()) {
                        policyProjections.add(new PolicyProjection(
                                dto.id(),
                                dto.routeId(),
                                dto.kind(),
                                dto.capacity(),
                                dto.refillTokens(),
                                dto.refillPeriodSeconds(),
                                dto.quotaLimit(),
                                dto.quotaPeriod(),
                                dto.enabled()
                        ));
                    }
                }
                PlanProjection planProjection = new PlanProjection(
                        planEvent.planId(),
                        planEvent.workspaceId(),
                        planEvent.productId(),
                        planEvent.name(),
                        planEvent.slug(),
                        planEvent.status(),
                        policyProjections,
                        planEvent.version()
                );
                String planKey = "rf:v1:cfg:plan:" + planEvent.planId();
                redisTemplate.opsForValue().set(planKey, objectMapper.writeValueAsString(planProjection));
                redisTemplate.opsForValue().set(versionKey, String.valueOf(incomingVersion));
                log.info("Projected plan {} to Redis key {}", planEvent.planId(), planKey);

            } else if ("SubscriptionConfigurationChangedV1".equals(eventType) || "Subscription".equalsIgnoreCase(aggregateType) || "SUBSCRIPTION".equalsIgnoreCase(aggregateType)) {
                SubscriptionConfigurationChangedV1 subEvent = objectMapper.convertValue(payload, SubscriptionConfigurationChangedV1.class);
                String subKey = "rf:v1:cfg:subscription:" + subEvent.subscriptionId();
                String appSubKey = "rf:v1:cfg:app-sub:" + subEvent.applicationId() + ":" + subEvent.productId();

                // Lookup plan policies if available
                List<PolicyProjection> policies = new ArrayList<>();
                String planKey = "rf:v1:cfg:plan:" + subEvent.planId();
                String planJson = redisTemplate.opsForValue().get(planKey);
                if (planJson != null) {
                    try {
                        PlanProjection planProj = objectMapper.readValue(planJson, PlanProjection.class);
                        if (planProj.policies() != null) {
                            policies.addAll(planProj.policies());
                        }
                    } catch (Exception ignored) {}
                }

                SubscriptionProjection projection = new SubscriptionProjection(
                        subEvent.subscriptionId(),
                        subEvent.workspaceId(),
                        subEvent.consumerId(),
                        subEvent.applicationId(),
                        subEvent.productId(),
                        subEvent.planId(),
                        subEvent.status(),
                        policies,
                        subEvent.effectiveFrom(),
                        subEvent.effectiveTo(),
                        subEvent.version()
                );
                redisTemplate.opsForValue().set(subKey, objectMapper.writeValueAsString(projection));
                redisTemplate.opsForValue().set(appSubKey, subEvent.subscriptionId().toString());
                redisTemplate.opsForValue().set(versionKey, String.valueOf(incomingVersion));
                log.info("Projected subscription {} (app-sub={}) to Redis", subEvent.subscriptionId(), appSubKey);
            }

        } catch (Exception e) {
            log.error("Failed to process config projection event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
