package io.meterforge.controlplane.product.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String slug,
        String upstreamBaseUrl,
        String gatewayBasePath,
        ResourceStatus status,
        int routeCount,
        Instant createdAt,
        Instant updatedAt,
        long version
) {}
