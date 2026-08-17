package io.meterforge.controlplane.workspace.api.dto;

public record UpdateWorkspaceRequest(
        String name,
        String status
) {}
