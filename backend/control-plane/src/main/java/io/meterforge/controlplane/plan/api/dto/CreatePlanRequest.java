package io.meterforge.controlplane.plan.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePlanRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotBlank(message = "Plan name is required")
        @Size(max = 255, message = "Plan name cannot exceed 255 characters")
        String name,

        @NotBlank(message = "Plan slug is required")
        @Size(max = 100, message = "Plan slug cannot exceed 100 characters")
        String slug,

        List<CreateLimitPolicyRequest> policies
) {}
