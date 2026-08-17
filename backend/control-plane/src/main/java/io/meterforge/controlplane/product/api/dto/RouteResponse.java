package io.meterforge.controlplane.product.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import java.time.Instant;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        UUID workspaceId,
        UUID productId,
        String httpMethod,
        String pathPattern,
        String upstreamPath,
        int costUnits,
        int priority,
        ResourceStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {}
