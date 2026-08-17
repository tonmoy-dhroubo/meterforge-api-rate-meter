package io.meterforge.controlplane.usage.api.dto;

import java.util.UUID;

public record TopRouteDto(
        UUID routeId,
        String httpMethod,
        String pathPattern,
        long totalRequests,
        long totalUnits
) {}
