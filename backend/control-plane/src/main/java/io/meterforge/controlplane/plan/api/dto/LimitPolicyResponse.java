package io.meterforge.controlplane.plan.api.dto;

import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;
import io.meterforge.controlplane.plan.domain.LimitPolicy;

import java.time.Instant;
import java.util.UUID;

public record LimitPolicyResponse(
        UUID id,
        UUID workspaceId,
        UUID planId,
        UUID routeId,
        LimitPolicyKind kind,
        Integer capacity,
        Integer refillTokens,
        Integer refillPeriodSeconds,
        Long quotaLimit,
        QuotaPeriod quotaPeriod,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static LimitPolicyResponse from(LimitPolicy policy) {
        return new LimitPolicyResponse(
                policy.getId(),
                policy.getWorkspaceId(),
                policy.getPlanId(),
                policy.getRouteId(),
                policy.getKind(),
                policy.getCapacity(),
                policy.getRefillTokens(),
                policy.getRefillPeriodSeconds(),
                policy.getQuotaLimit(),
                policy.getQuotaPeriod(),
                policy.isEnabled(),
                policy.getCreatedAt(),
                policy.getUpdatedAt(),
                policy.getVersion()
        );
    }
}
