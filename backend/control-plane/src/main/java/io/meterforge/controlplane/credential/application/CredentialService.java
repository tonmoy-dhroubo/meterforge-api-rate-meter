package io.meterforge.controlplane.credential.application;

import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.CredentialConfigurationChangedV1;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.consumer.domain.ConsumerApplication;
import io.meterforge.controlplane.consumer.domain.ConsumerApplicationRepository;
import io.meterforge.controlplane.credential.domain.ApiCredential;
import io.meterforge.controlplane.credential.domain.ApiCredentialRepository;
import io.meterforge.controlplane.credential.domain.ApiKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CredentialService {

    private final ApiCredentialRepository credentialRepository;
    private final ConsumerApplicationRepository applicationRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final TransactionalMutationService mutationService;

    public CredentialService(ApiCredentialRepository credentialRepository,
                             ConsumerApplicationRepository applicationRepository,
                             ApiKeyGenerator apiKeyGenerator,
                             TransactionalMutationService mutationService) {
        this.credentialRepository = credentialRepository;
        this.applicationRepository = applicationRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.mutationService = mutationService;
    }

    public record IssueResult(ApiCredential credential, String rawKey) {}

    @Transactional(readOnly = true)
    public List<ApiCredential> listCredentials(UUID workspaceId, UUID applicationId) {
        return credentialRepository.findByWorkspaceIdAndApplicationIdOrderByCreatedAtDesc(workspaceId, applicationId);
    }

    @Transactional(readOnly = true)
    public ApiCredential getCredential(UUID workspaceId, UUID credentialId) {
        return credentialRepository.findByIdAndWorkspaceId(credentialId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiCredential", credentialId));
    }

    @Transactional
    public IssueResult issueCredential(UUID workspaceId, UUID userId, UUID applicationId, String environment, Instant expiresAt) {
        ConsumerApplication app = applicationRepository.findByIdAndWorkspaceId(applicationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("ConsumerApplication", applicationId));

        ApiKeyGenerator.GeneratedApiKey generated = apiKeyGenerator.generateKey(environment);

        ApiCredential credential = new ApiCredential(
                null,
                workspaceId,
                app.getId(),
                generated.publicId(),
                generated.secretHmac(),
                generated.displayPrefix(),
                generated.displayLastFour(),
                generated.environment(),
                expiresAt
        );
        credential = credentialRepository.save(credential);

        CredentialConfigurationChangedV1 payload = new CredentialConfigurationChangedV1(
                credential.getId(),
                credential.getWorkspaceId(),
                app.getConsumerId(),
                credential.getApplicationId(),
                credential.getPublicId(),
                credential.getSecretHmac(),
                credential.getDisplayPrefix(),
                credential.getDisplayLastFour(),
                credential.getEnvironment(),
                credential.getStatus(),
                credential.getExpiresAt(),
                credential.getRevokedAt(),
                credential.getVersion(),
                credential.getUpdatedAt()
        );

        ConfigEventEnvelope<CredentialConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ApiCredential",
                credential.getId(),
                credential.getVersion(),
                "CredentialConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "ISSUE_CREDENTIAL",
                "ApiCredential",
                credential.getId(),
                null,
                "Issued API key (" + credential.getDisplayPrefix() + "..." + credential.getDisplayLastFour() + ") for application '" + app.getName() + "'",
                Map.of("publicId", credential.getPublicId(), "environment", credential.getEnvironment()),
                outboxEvent
        );

        return new IssueResult(credential, generated.fullKey());
    }

    @Transactional
    public ApiCredential revokeCredential(UUID workspaceId, UUID userId, UUID credentialId) {
        ApiCredential credential = getCredential(workspaceId, credentialId);
        ConsumerApplication app = applicationRepository.findByIdAndWorkspaceId(credential.getApplicationId(), workspaceId)
                .orElse(null);
        UUID consumerId = app != null ? app.getConsumerId() : null;
        credential.revoke();
        credential = credentialRepository.save(credential);

        CredentialConfigurationChangedV1 payload = new CredentialConfigurationChangedV1(
                credential.getId(),
                credential.getWorkspaceId(),
                consumerId,
                credential.getApplicationId(),
                credential.getPublicId(),
                credential.getSecretHmac(),
                credential.getDisplayPrefix(),
                credential.getDisplayLastFour(),
                credential.getEnvironment(),
                credential.getStatus(),
                credential.getExpiresAt(),
                credential.getRevokedAt(),
                credential.getVersion(),
                credential.getUpdatedAt()
        );

        ConfigEventEnvelope<CredentialConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ApiCredential",
                credential.getId(),
                credential.getVersion(),
                "CredentialConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "REVOKE_CREDENTIAL",
                "ApiCredential",
                credential.getId(),
                null,
                "Revoked API key (" + credential.getDisplayPrefix() + "..." + credential.getDisplayLastFour() + ")",
                Map.of("publicId", credential.getPublicId()),
                outboxEvent
        );

        return credential;
    }

    @Transactional
    public IssueResult rotateCredential(UUID workspaceId, UUID userId, UUID credentialId) {
        ApiCredential oldCredential = revokeCredential(workspaceId, userId, credentialId);
        return issueCredential(workspaceId, userId, oldCredential.getApplicationId(), oldCredential.getEnvironment(), oldCredential.getExpiresAt());
    }
}
