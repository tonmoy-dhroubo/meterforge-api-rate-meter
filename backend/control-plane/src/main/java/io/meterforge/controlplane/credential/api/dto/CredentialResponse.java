package io.meterforge.controlplane.credential.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.credential.domain.ApiCredential;

import java.time.Instant;
import java.util.UUID;

public record CredentialResponse(
        UUID id,
        UUID workspaceId,
        UUID applicationId,
        String publicId,
        String displayPrefix,
        String displayLastFour,
        String environment,
        ResourceStatus status,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static CredentialResponse from(ApiCredential credential) {
        return new CredentialResponse(
                credential.getId(),
                credential.getWorkspaceId(),
                credential.getApplicationId(),
                credential.getPublicId(),
                credential.getDisplayPrefix(),
                credential.getDisplayLastFour(),
                credential.getEnvironment(),
                credential.getStatus(),
                credential.getExpiresAt(),
                credential.getRevokedAt(),
                credential.getCreatedAt(),
                credential.getUpdatedAt(),
                credential.getVersion()
        );
    }
}
