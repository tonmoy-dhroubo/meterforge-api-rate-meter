package io.meterforge.gateway.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.projection.PlanProjection;
import io.meterforge.contracts.projection.PolicyProjection;
import io.meterforge.contracts.projection.SubscriptionProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class SubscriptionResolver {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionResolver.class);
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SubscriptionResolver(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void clearCache() {
        // No-op
    }

    public Mono<SubscriptionProjection> resolve(UUID applicationId, UUID productId) {
        String appSubKey = "rf:v1:cfg:app-sub:" + applicationId + ":" + productId;
        return redisTemplate.opsForValue().get(appSubKey)
                .flatMap(subIdStr -> {
                    String subKey = "rf:v1:cfg:subscription:" + subIdStr;
                    return redisTemplate.opsForValue().get(subKey);
                })
                .flatMap(subJson -> {
                    try {
                        SubscriptionProjection sub = objectMapper.readValue(subJson, SubscriptionProjection.class);
                        return hydratePoliciesIfNeeded(sub);
                    } catch (Exception e) {
                        log.error("Failed to parse subscription projection: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .filter(this::isActiveSubscription);
    }

    private Mono<SubscriptionProjection> hydratePoliciesIfNeeded(SubscriptionProjection sub) {
        if (sub.policies() != null && !sub.policies().isEmpty()) {
            return Mono.just(sub);
        }

        String planKey = "rf:v1:cfg:plan:" + sub.planId();
        return redisTemplate.opsForValue().get(planKey)
                .mapNotNull(planJson -> {
                    try {
                        PlanProjection plan = objectMapper.readValue(planJson, PlanProjection.class);
                        List<PolicyProjection> policies = plan.policies() != null ? plan.policies() : List.of();
                        return new SubscriptionProjection(
                                sub.subscriptionId(),
                                sub.workspaceId(),
                                sub.applicationId(),
                                sub.productId(),
                                sub.planId(),
                                sub.status(),
                                policies,
                                sub.effectiveFrom(),
                                sub.effectiveTo(),
                                sub.version()
                        );
                    } catch (Exception e) {
                        return sub;
                    }
                })
                .defaultIfEmpty(sub);
    }

    private boolean isActiveSubscription(SubscriptionProjection sub) {
        if (sub.status() != ResourceStatus.ACTIVE) {
            return false;
        }

        Instant now = Instant.now();
        if (sub.effectiveFrom() != null && now.isBefore(sub.effectiveFrom())) {
            return false;
        }
        if (sub.effectiveTo() != null && now.isAfter(sub.effectiveTo())) {
            return false;
        }

        return true;
    }
}
