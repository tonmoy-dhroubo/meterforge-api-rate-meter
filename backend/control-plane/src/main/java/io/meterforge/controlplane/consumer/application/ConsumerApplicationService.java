package io.meterforge.controlplane.consumer.application;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.event.ApplicationConfigurationChangedV1;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.ResourceConflictException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.consumer.domain.ConsumerApplication;
import io.meterforge.controlplane.consumer.domain.ConsumerApplicationRepository;
import io.meterforge.controlplane.consumer.domain.ConsumerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsumerApplicationService {

    private final ConsumerApplicationRepository applicationRepository;
    private final ConsumerRepository consumerRepository;
    private final TransactionalMutationService mutationService;

    public ConsumerApplicationService(ConsumerApplicationRepository applicationRepository,
                                      ConsumerRepository consumerRepository,
                                      TransactionalMutationService mutationService) {
        this.applicationRepository = applicationRepository;
        this.consumerRepository = consumerRepository;
        this.mutationService = mutationService;
    }

    @Transactional(readOnly = true)
    public List<ConsumerApplication> listApplications(UUID workspaceId, UUID consumerId) {
        if (!consumerRepository.existsById(consumerId)) {
            throw new ResourceNotFoundException("Consumer", consumerId);
        }
        return applicationRepository.findByWorkspaceIdAndConsumerIdOrderByCreatedAtDesc(workspaceId, consumerId);
    }

    @Transactional(readOnly = true)
    public List<ConsumerApplication> listAllWorkspaceApplications(UUID workspaceId) {
        return applicationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public ConsumerApplication getApplication(UUID workspaceId, UUID applicationId) {
        return applicationRepository.findByIdAndWorkspaceId(applicationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("ConsumerApplication", applicationId));
    }

    @Transactional
    public ConsumerApplication createApplication(UUID workspaceId, UUID userId, UUID consumerId, String name) {
        consumerRepository.findByIdAndWorkspaceId(consumerId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer", consumerId));

        String trimmedName = name.trim();
        if (applicationRepository.existsByConsumerIdAndName(consumerId, trimmedName)) {
            throw new ResourceConflictException("An application with name '" + trimmedName + "' already exists for this consumer.");
        }

        ConsumerApplication app = new ConsumerApplication(null, workspaceId, consumerId, trimmedName);
        app = applicationRepository.saveAndFlush(app);

        ApplicationConfigurationChangedV1 payload = new ApplicationConfigurationChangedV1(
                app.getId(),
                app.getWorkspaceId(),
                app.getConsumerId(),
                app.getName(),
                app.getStatus(),
                app.getVersion(),
                app.getUpdatedAt()
        );

        ConfigEventEnvelope<ApplicationConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ConsumerApplication",
                app.getId(),
                app.getVersion(),
                "ApplicationConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "CREATE_APPLICATION",
                "ConsumerApplication",
                app.getId(),
                null,
                "Created application '" + app.getName() + "'",
                Map.of("name", app.getName()),
                outboxEvent
        );

        return app;
    }

    @Transactional
    public ConsumerApplication updateApplication(UUID workspaceId, UUID userId, UUID applicationId, String name) {
        ConsumerApplication app = getApplication(workspaceId, applicationId);
        app.update(name);
        app = applicationRepository.saveAndFlush(app);

        ApplicationConfigurationChangedV1 payload = new ApplicationConfigurationChangedV1(
                app.getId(),
                app.getWorkspaceId(),
                app.getConsumerId(),
                app.getName(),
                app.getStatus(),
                app.getVersion(),
                app.getUpdatedAt()
        );

        ConfigEventEnvelope<ApplicationConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ConsumerApplication",
                app.getId(),
                app.getVersion(),
                "ApplicationConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "UPDATE_APPLICATION",
                "ConsumerApplication",
                app.getId(),
                null,
                "Updated application '" + app.getName() + "'",
                Map.of("name", app.getName()),
                outboxEvent
        );

        return app;
    }

    @Transactional
    public ConsumerApplication activateApplication(UUID workspaceId, UUID userId, UUID applicationId) {
        ConsumerApplication app = getApplication(workspaceId, applicationId);
        app.setStatus(ResourceStatus.ACTIVE);
        app = applicationRepository.saveAndFlush(app);

        ApplicationConfigurationChangedV1 payload = new ApplicationConfigurationChangedV1(
                app.getId(),
                app.getWorkspaceId(),
                app.getConsumerId(),
                app.getName(),
                app.getStatus(),
                app.getVersion(),
                app.getUpdatedAt()
        );

        ConfigEventEnvelope<ApplicationConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ConsumerApplication",
                app.getId(),
                app.getVersion(),
                "ApplicationConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "ACTIVATE_APPLICATION",
                "ConsumerApplication",
                app.getId(),
                null,
                "Activated application '" + app.getName() + "'",
                Map.of("name", app.getName()),
                outboxEvent
        );

        return app;
    }

    @Transactional
    public ConsumerApplication disableApplication(UUID workspaceId, UUID userId, UUID applicationId) {
        ConsumerApplication app = getApplication(workspaceId, applicationId);
        app.setStatus(ResourceStatus.DISABLED);
        app = applicationRepository.saveAndFlush(app);

        ApplicationConfigurationChangedV1 payload = new ApplicationConfigurationChangedV1(
                app.getId(),
                app.getWorkspaceId(),
                app.getConsumerId(),
                app.getName(),
                app.getStatus(),
                app.getVersion(),
                app.getUpdatedAt()
        );

        ConfigEventEnvelope<ApplicationConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "ConsumerApplication",
                app.getId(),
                app.getVersion(),
                "ApplicationConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "DISABLE_APPLICATION",
                "ConsumerApplication",
                app.getId(),
                null,
                "Disabled application '" + app.getName() + "'",
                Map.of("name", app.getName()),
                outboxEvent
        );

        return app;
    }
}
