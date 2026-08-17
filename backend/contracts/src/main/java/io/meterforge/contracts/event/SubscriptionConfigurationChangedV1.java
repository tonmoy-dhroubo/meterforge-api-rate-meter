package io.meterforge.contracts.event;

import io.meterforge.contracts.common.ResourceStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionConfigurationChangedV1(
        UUID subscriptionId,
        UUID workspaceId,
        UUID applicationId,
        UUID productId,
        UUID planId,
        ResourceStatus status,
        Instant effectiveFrom,
        Instant effectiveTo,
        long version,
        Instant updatedAt
) {}
