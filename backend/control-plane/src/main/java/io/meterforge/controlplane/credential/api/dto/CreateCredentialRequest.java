package io.meterforge.controlplane.credential.api.dto;

import java.time.Instant;

public record CreateCredentialRequest(
        String environment,
        Instant expiresAt
) {}
