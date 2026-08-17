package io.meterforge.controlplane.subscription.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSubscriptionRequest(
        @NotNull(message = "Application ID is required")
        UUID applicationId,

        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Plan ID is required")
        UUID planId
) {}
