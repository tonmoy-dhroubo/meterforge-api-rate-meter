package io.meterforge.contracts.projection;

import io.meterforge.contracts.common.ResourceStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubscriptionProjection(
        UUID subscriptionId,
        UUID workspaceId,
        UUID applicationId,
        UUID productId,
        UUID planId,
        ResourceStatus status,
        List<PolicyProjection> policies,
        Instant effectiveFrom,
        Instant effectiveTo,
        long version
) {}
