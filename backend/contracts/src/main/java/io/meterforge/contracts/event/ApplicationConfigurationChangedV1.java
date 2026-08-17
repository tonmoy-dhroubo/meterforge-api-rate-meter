package io.meterforge.contracts.event;

import io.meterforge.contracts.common.ResourceStatus;

import java.time.Instant;
import java.util.UUID;

public record ApplicationConfigurationChangedV1(
        UUID applicationId,
        UUID workspaceId,
        UUID consumerId,
        String name,
        ResourceStatus status,
        long version,
        Instant updatedAt
) {}
