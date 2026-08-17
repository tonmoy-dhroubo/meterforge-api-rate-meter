package io.meterforge.controlplane.consumer.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.consumer.domain.Consumer;

import java.time.Instant;
import java.util.UUID;

public record ConsumerResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String externalReference,
        ResourceStatus status,
        long applicationCount,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static ConsumerResponse from(Consumer consumer, long applicationCount) {
        return new ConsumerResponse(
                consumer.getId(),
                consumer.getWorkspaceId(),
                consumer.getName(),
                consumer.getExternalReference(),
                consumer.getStatus(),
                applicationCount,
                consumer.getCreatedAt(),
                consumer.getUpdatedAt(),
                consumer.getVersion()
        );
    }
}
