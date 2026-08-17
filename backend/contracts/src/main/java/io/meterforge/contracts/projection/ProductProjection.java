package io.meterforge.contracts.projection;

import io.meterforge.contracts.common.ResourceStatus;

import java.util.List;
import java.util.UUID;

public record ProductProjection(
        UUID productId,
        UUID workspaceId,
        String name,
        String slug,
        String upstreamBaseUrl,
        String gatewayBasePath,
        ResourceStatus status,
        List<RouteProjection> routes,
        long version
) {}
