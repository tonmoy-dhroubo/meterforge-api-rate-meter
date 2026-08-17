package io.meterforge.contracts.event;

import io.meterforge.contracts.common.ResourceStatus;

import java.time.Instant;
import java.util.UUID;

public record ConsumerConfigurationChangedV1(
        UUID consumerId,
        UUID workspaceId,
        String name,
        String externalReference,
        ResourceStatus status,
        long version,
        Instant updatedAt
) {}
