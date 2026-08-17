package io.meterforge.controlplane.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "meterforge")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected OutboxEvent() {}

    public OutboxEvent(
            UUID id,
            UUID eventId,
            UUID workspaceId,
            String aggregateType,
            UUID aggregateId,
            long aggregateVersion,
            String eventType,
            int schemaVersion,
            String payload,
            Instant occurredAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId cannot be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType cannot be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        this.aggregateVersion = aggregateVersion;
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.schemaVersion = schemaVersion;
        this.payload = Objects.requireNonNull(payload, "payload cannot be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        this.attemptCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void recordAttempt() {
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
    }
}
