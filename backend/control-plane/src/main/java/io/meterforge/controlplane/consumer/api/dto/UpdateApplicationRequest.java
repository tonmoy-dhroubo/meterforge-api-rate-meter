package io.meterforge.controlplane.consumer.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateApplicationRequest(
        @Size(max = 255, message = "Application name cannot exceed 255 characters")
        String name
) {}
