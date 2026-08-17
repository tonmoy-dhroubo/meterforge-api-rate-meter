package io.meterforge.controlplane.workspace.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateWorkspaceRequest(
        @NotBlank(message = "Workspace name is required")
        String name,

        @NotBlank(message = "Workspace slug is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        String slug
) {}
