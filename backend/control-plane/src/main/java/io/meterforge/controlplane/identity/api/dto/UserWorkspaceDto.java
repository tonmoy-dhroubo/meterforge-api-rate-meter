package io.meterforge.controlplane.identity.api.dto;

import io.meterforge.contracts.common.Role;
import java.util.UUID;

public record UserWorkspaceDto(
        UUID id,
        String name,
        String slug,
        Role role
) {}
