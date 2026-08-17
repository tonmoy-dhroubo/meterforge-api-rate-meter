package io.meterforge.controlplane.workspace.api.dto;

import io.meterforge.contracts.common.Role;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
        UUID userId,
        String email,
        Role role,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
