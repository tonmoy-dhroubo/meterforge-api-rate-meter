package io.meterforge.controlplane.workspace.api.dto;

import io.meterforge.contracts.common.Role;

public record UpdateWorkspaceMemberRequest(
        Role role,
        String status
) {}
