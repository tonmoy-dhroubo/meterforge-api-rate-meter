package io.meterforge.controlplane.identity.api.dto;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String status,
        List<UserWorkspaceDto> workspaces
) {}
