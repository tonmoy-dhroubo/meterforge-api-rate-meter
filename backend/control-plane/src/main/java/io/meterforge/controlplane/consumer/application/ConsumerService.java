package io.meterforge.controlplane.consumer.application;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.ConsumerConfigurationChangedV1;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.ResourceConflictException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.consumer.domain.Consumer;
import io.meterforge.controlplane.consumer.domain.ConsumerApplicationRepository;
import io.meterforge.controlplane.consumer.domain.ConsumerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsumerService {

    private final ConsumerRepository consumerRepository;
    private final ConsumerApplicationRepository applicationRepository;
    private final TransactionalMutationService mutationService;

    public ConsumerService(ConsumerRepository consumerRepository,
                           ConsumerApplicationRepository applicationRepository,
                           TransactionalMutationService mutationService) {
        this.consumerRepository = consumerRepository;
        this.applicationRepository = applicationRepository;
        this.mutationService = mutationService;
    }

    @Transactional(readOnly = true)
    public List<Consumer> listConsumers(UUID workspaceId) {
        return consumerRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Consumer getConsumer(UUID workspaceId, UUID consumerId) {
        return consumerRepository.findByIdAndWorkspaceId(consumerId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer", consumerId));
    }

    @Transactional(readOnly = true)
    public long getApplicationCount(UUID workspaceId, UUID consumerId) {
        return applicationRepository.countByWorkspaceIdAndConsumerId(workspaceId, consumerId);
    }

    @Transactional
    public Consumer createConsumer(UUID workspaceId, UUID userId, String name, String externalReference) {
        String trimmedName = name.trim();
        if (consumerRepository.existsByWorkspaceIdAndName(workspaceId, trimmedName)) {
            throw new ResourceConflictException("A consumer with name '" + trimmedName + "' already exists in this workspace.");
        }

        Consumer consumer = new Consumer(null, workspaceId, trimmedName, externalReference != null ? externalReference.trim() : null);
        consumer = consumerRepository.save(consumer);

        ConsumerConfigurationChangedV1 payload = new ConsumerConfigurationChangedV1(
                consumer.getId(),
                consumer.getWorkspaceId(),
                consumer.getName(),
                consumer.getExternalReference(),
                consumer.getStatus(),
                consumer.getVersion(),
                consumer.getUpdatedAt()
        );

        ConfigEventEnvelope<ConsumerConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "Consumer",
                consumer.getId(),
                consumer.getVersion(),
                "ConsumerConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "CREATE_CONSUMER",
                "Consumer",
                consumer.getId(),
                null,
                "Created consumer '" + consumer.getName() + "'",
                Map.of("name", consumer.getName()),
                outboxEvent
        );

        return consumer;
    }

    @Transactional
    public Consumer updateConsumer(UUID workspaceId, UUID userId, UUID consumerId, String name, String externalReference) {
        Consumer consumer = getConsumer(workspaceId, consumerId);
        consumer.update(name, externalReference);
        consumer = consumerRepository.save(consumer);

        ConsumerConfigurationChangedV1 payload = new ConsumerConfigurationChangedV1(
                consumer.getId(),
                consumer.getWorkspaceId(),
                consumer.getName(),
                consumer.getExternalReference(),
                consumer.getStatus(),
                consumer.getVersion(),
                consumer.getUpdatedAt()
        );

        ConfigEventEnvelope<ConsumerConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "Consumer",
                consumer.getId(),
                consumer.getVersion(),
                "ConsumerConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "UPDATE_CONSUMER",
                "Consumer",
                consumer.getId(),
                null,
                "Updated consumer '" + consumer.getName() + "'",
                Map.of("name", consumer.getName()),
                outboxEvent
        );

        return consumer;
    }

    @Transactional
    public Consumer activateConsumer(UUID workspaceId, UUID userId, UUID consumerId) {
        Consumer consumer = getConsumer(workspaceId, consumerId);
        consumer.setStatus(ResourceStatus.ACTIVE);
        consumer = consumerRepository.save(consumer);

        ConsumerConfigurationChangedV1 payload = new ConsumerConfigurationChangedV1(
                consumer.getId(),
                consumer.getWorkspaceId(),
                consumer.getName(),
                consumer.getExternalReference(),
                consumer.getStatus(),
                consumer.getVersion(),
                consumer.getUpdatedAt()
        );

        ConfigEventEnvelope<ConsumerConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "Consumer",
                consumer.getId(),
                consumer.getVersion(),
                "ConsumerConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "ACTIVATE_CONSUMER",
                "Consumer",
                consumer.getId(),
                null,
                "Activated consumer '" + consumer.getName() + "'",
                Map.of("name", consumer.getName()),
                outboxEvent
        );

        return consumer;
    }

    @Transactional
    public Consumer disableConsumer(UUID workspaceId, UUID userId, UUID consumerId) {
        Consumer consumer = getConsumer(workspaceId, consumerId);
        consumer.setStatus(ResourceStatus.DISABLED);
        consumer = consumerRepository.save(consumer);

        ConsumerConfigurationChangedV1 payload = new ConsumerConfigurationChangedV1(
                consumer.getId(),
                consumer.getWorkspaceId(),
                consumer.getName(),
                consumer.getExternalReference(),
                consumer.getStatus(),
                consumer.getVersion(),
                consumer.getUpdatedAt()
        );

        ConfigEventEnvelope<ConsumerConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "Consumer",
                consumer.getId(),
                consumer.getVersion(),
                "ConsumerConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "DISABLE_CONSUMER",
                "Consumer",
                consumer.getId(),
                null,
                "Disabled consumer '" + consumer.getName() + "'",
                Map.of("name", consumer.getName()),
                outboxEvent
        );

        return consumer;
    }
}
