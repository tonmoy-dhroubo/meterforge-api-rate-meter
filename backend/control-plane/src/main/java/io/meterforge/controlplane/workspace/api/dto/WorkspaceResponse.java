package io.meterforge.controlplane.workspace.api.dto;

import io.meterforge.contracts.common.Role;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        String status,
        Role currentUserRole,
        Instant createdAt,
        Instant updatedAt,
        long version
) {}
