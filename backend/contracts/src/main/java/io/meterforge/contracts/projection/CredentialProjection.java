package io.meterforge.contracts.projection;

import io.meterforge.contracts.common.ResourceStatus;

import java.time.Instant;
import java.util.UUID;

public record CredentialProjection(
        UUID credentialId,
        UUID workspaceId,
        UUID applicationId,
        String publicId,
        String secretHmac,
        String environment,
        ResourceStatus status,
        Instant expiresAt,
        Instant revokedAt,
        long version
) {}
