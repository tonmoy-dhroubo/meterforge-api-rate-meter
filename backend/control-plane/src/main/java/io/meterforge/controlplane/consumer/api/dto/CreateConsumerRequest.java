package io.meterforge.controlplane.consumer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConsumerRequest(
        @NotBlank(message = "Consumer name is required")
        @Size(max = 255, message = "Consumer name cannot exceed 255 characters")
        String name,

        @Size(max = 255, message = "External reference cannot exceed 255 characters")
        String externalReference
) {}
