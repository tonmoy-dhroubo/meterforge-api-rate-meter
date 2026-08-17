package io.meterforge.controlplane.credential.api.dto;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.controlplane.credential.domain.ApiCredential;

import java.time.Instant;
import java.util.UUID;

public record CreateCredentialResponse(
        UUID id,
        UUID workspaceId,
        UUID applicationId,
        String publicId,
        String rawKey,
        String displayPrefix,
        String displayLastFour,
        String environment,
        ResourceStatus status,
        Instant expiresAt,
        Instant createdAt,
        long version
) {
    public static CreateCredentialResponse from(ApiCredential credential, String rawKey) {
        return new CreateCredentialResponse(
                credential.getId(),
                credential.getWorkspaceId(),
                credential.getApplicationId(),
                credential.getPublicId(),
                rawKey,
                credential.getDisplayPrefix(),
                credential.getDisplayLastFour(),
                credential.getEnvironment(),
                credential.getStatus(),
                credential.getExpiresAt(),
                credential.getCreatedAt(),
                credential.getVersion()
        );
    }
}
