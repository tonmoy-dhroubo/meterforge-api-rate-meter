package io.meterforge.controlplane.product.api.dto;

import jakarta.validation.constraints.Min;

public record UpdateRouteRequest(
        String upstreamPath,

        @Min(value = 1, message = "Cost units must be at least 1")
        Integer costUnits,

        @Min(value = 0, message = "Priority must be non-negative")
        Integer priority
) {}
