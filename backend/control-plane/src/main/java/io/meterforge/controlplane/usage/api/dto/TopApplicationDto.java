package io.meterforge.controlplane.usage.api.dto;

import java.util.UUID;

public record TopApplicationDto(
        UUID applicationId,
        String applicationName,
        long totalRequests,
        long totalUnits
) {}
