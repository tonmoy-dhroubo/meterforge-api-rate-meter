package io.meterforge.controlplane.product.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRouteRequest(
        @NotBlank(message = "HTTP method is required")
        @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$", message = "Invalid HTTP method")
        String httpMethod,

        @NotBlank(message = "Path pattern is required")
        String pathPattern,

        String upstreamPath,

        @Min(value = 1, message = "Cost units must be at least 1")
        Integer costUnits,

        @Min(value = 0, message = "Priority must be non-negative")
        Integer priority
) {}
