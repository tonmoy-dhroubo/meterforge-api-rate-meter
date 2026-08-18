package io.meterforge.controlplane.identity.api.dto;

import java.util.List;

public record UserProfileResponse(
        UserSummaryDto user,
        List<UserWorkspaceDto> memberships
) {}

