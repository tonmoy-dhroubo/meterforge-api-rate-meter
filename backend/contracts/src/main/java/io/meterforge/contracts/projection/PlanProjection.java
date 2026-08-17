package io.meterforge.contracts.projection;

import io.meterforge.contracts.common.ResourceStatus;

import java.util.List;
import java.util.UUID;

public record PlanProjection(
        UUID planId,
        UUID workspaceId,
        UUID productId,
        String name,
        String slug,
        ResourceStatus status,
        List<PolicyProjection> policies,
        long version
) {}
