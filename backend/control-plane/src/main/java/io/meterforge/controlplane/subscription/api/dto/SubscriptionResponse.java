package io.meterforge.controlplane.subscription.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.subscription.domain.Subscription;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID workspaceId,
        UUID applicationId,
        UUID productId,
        UUID planId,
        ResourceStatus status,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static SubscriptionResponse from(Subscription sub) {
        return new SubscriptionResponse(
                sub.getId(),
                sub.getWorkspaceId(),
                sub.getApplicationId(),
                sub.getProductId(),
                sub.getPlanId(),
                sub.getStatus(),
                sub.getEffectiveFrom(),
                sub.getEffectiveTo(),
                sub.getCreatedAt(),
                sub.getUpdatedAt(),
                sub.getVersion()
        );
    }
}
