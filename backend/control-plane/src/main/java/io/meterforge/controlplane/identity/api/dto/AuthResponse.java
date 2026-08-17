package io.meterforge.controlplane.identity.api.dto;

public record AuthResponse(
        UserProfileResponse user,
        String token
) {}
