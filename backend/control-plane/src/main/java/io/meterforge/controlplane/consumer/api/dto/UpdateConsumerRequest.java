package io.meterforge.controlplane.consumer.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateConsumerRequest(
        @Size(max = 255, message = "Consumer name cannot exceed 255 characters")
        String name,

        @Size(max = 255, message = "External reference cannot exceed 255 characters")
        String externalReference
) {}
