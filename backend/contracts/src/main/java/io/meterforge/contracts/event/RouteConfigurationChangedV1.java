package io.meterforge.contracts.event;

import io.meterforge.contracts.common.ResourceStatus;
import java.util.UUID;

public record RouteConfigurationChangedV1(
        UUID routeId,
        UUID productId,
        UUID workspaceId,
        String httpMethod,
        String pathPattern,
        String upstreamPath,
        int costUnits,
        int priority,
        ResourceStatus status,
        long version
) {}
