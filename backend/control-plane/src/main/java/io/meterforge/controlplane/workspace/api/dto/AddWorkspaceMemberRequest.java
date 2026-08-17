package io.meterforge.controlplane.workspace.api.dto;

import io.meterforge.contracts.common.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddWorkspaceMemberRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotNull(message = "Role is required")
        Role role
) {}
