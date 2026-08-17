package io.meterforge.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record ConfigEventEnvelope<T>(
        int schemaVersion,
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        T payload
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static <T> ConfigEventEnvelope<T> of(
            UUID workspaceId,
            String aggregateType,
            UUID aggregateId,
            long aggregateVersion,
            String eventType,
            T payload
    ) {
        return new ConfigEventEnvelope<>(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                Instant.now(),
                workspaceId,
                aggregateType,
                aggregateId,
                aggregateVersion,
                eventType,
                payload
        );
    }
}
