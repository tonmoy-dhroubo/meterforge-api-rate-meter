package io.meterforge.controlplane.identity.api.dto;

import io.meterforge.contracts.common.Role;
import java.util.UUID;

public record UserWorkspaceDto(
        UUID workspaceId,
        String workspaceName,
        String workspaceSlug,
        Role role
) {}

