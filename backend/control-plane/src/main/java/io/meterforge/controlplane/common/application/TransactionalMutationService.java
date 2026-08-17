package io.meterforge.controlplane.common.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.controlplane.audit.domain.AuditLog;
import io.meterforge.controlplane.audit.domain.AuditLogRepository;
import io.meterforge.controlplane.outbox.domain.OutboxEvent;
import io.meterforge.controlplane.outbox.domain.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TransactionalMutationService {

    private final AuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransactionalMutationService(
            AuditLogRepository auditLogRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> void recordMutation(
            UUID workspaceId,
            UUID userId,
            String action,
            String resourceType,
            UUID resourceId,
            String requestId,
            String summary,
            Map<String, Object> auditMetadata,
            ConfigEventEnvelope<T> outboxPayload
    ) {
        // 1. Persist Audit Log
        AuditLog auditLog = new AuditLog(
                UUID.randomUUID(),
                workspaceId,
                userId,
                action,
                resourceType,
                resourceId,
                requestId,
                summary,
                auditMetadata
        );
        auditLogRepository.save(auditLog);

        // 2. Persist Outbox Event if provided
        if (outboxPayload != null) {
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(outboxPayload.payload());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize outbox event payload", e);
            }

            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.randomUUID(),
                    outboxPayload.eventId(),
                    workspaceId,
                    outboxPayload.aggregateType(),
                    outboxPayload.aggregateId(),
                    outboxPayload.aggregateVersion(),
                    outboxPayload.eventType(),
                    outboxPayload.schemaVersion(),
                    payloadJson,
                    outboxPayload.occurredAt()
            );
            outboxEventRepository.save(outboxEvent);
        }
    }
}
