package io.meterforge.controlplane.plan.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.plan.domain.Plan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        UUID workspaceId,
        UUID productId,
        String name,
        String slug,
        ResourceStatus status,
        List<LimitPolicyResponse> policies,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static PlanResponse from(Plan plan, List<LimitPolicyResponse> policies) {
        return new PlanResponse(
                plan.getId(),
                plan.getWorkspaceId(),
                plan.getProductId(),
                plan.getName(),
                plan.getSlug(),
                plan.getStatus(),
                policies != null ? policies : List.of(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getVersion()
        );
    }
}
