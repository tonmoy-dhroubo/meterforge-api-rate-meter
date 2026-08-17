package io.meterforge.contracts.event;

import io.meterforge.contracts.common.ResourceStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanConfigurationChangedV1(
        UUID planId,
        UUID workspaceId,
        UUID productId,
        String name,
        String slug,
        ResourceStatus status,
        List<LimitPolicyDto> policies,
        long version,
        Instant updatedAt
) {}
