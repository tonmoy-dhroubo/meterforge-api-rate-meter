package io.meterforge.controlplane.identity.api.dto;

import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String email,
        String status
) {}
