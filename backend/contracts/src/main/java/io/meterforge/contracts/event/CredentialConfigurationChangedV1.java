package io.meterforge.contracts.event;

import io.meterforge.contracts.common.ResourceStatus;

import java.time.Instant;
import java.util.UUID;

public record CredentialConfigurationChangedV1(
        UUID credentialId,
        UUID workspaceId,
        UUID consumerId,
        UUID applicationId,
        String publicId,
        String secretHmac,
        String displayPrefix,
        String displayLastFour,
        String environment,
        ResourceStatus status,
        Instant expiresAt,
        Instant revokedAt,
        long version,
        Instant updatedAt
) {}

