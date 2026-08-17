package io.meterforge.controlplane.consumer.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.consumer.domain.ConsumerApplication;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID workspaceId,
        UUID consumerId,
        String name,
        ResourceStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static ApplicationResponse from(ConsumerApplication app) {
        return new ApplicationResponse(
                app.getId(),
                app.getWorkspaceId(),
                app.getConsumerId(),
                app.getName(),
                app.getStatus(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                app.getVersion()
        );
    }
}
