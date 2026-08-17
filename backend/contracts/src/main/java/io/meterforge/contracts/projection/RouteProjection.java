package io.meterforge.contracts.projection;

import io.meterforge.contracts.common.ResourceStatus;

import java.util.UUID;

public record RouteProjection(
        UUID routeId,
        String httpMethod,
        String pathPattern,
        String upstreamPath,
        int costUnits,
        int priority,
        ResourceStatus status,
        long version
) {}
