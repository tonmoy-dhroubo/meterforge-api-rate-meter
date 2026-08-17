package io.meterforge.controlplane.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", schema = "meterforge")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(
            UUID id,
            UUID workspaceId,
            UUID userId,
            String action,
            String resourceType,
            UUID resourceId,
            String requestId,
            String summary,
            Map<String, Object> metadata
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId cannot be null");
        this.userId = userId;
        this.action = Objects.requireNonNull(action, "action cannot be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType cannot be null");
        this.resourceId = resourceId;
        this.requestId = requestId;
        this.summary = Objects.requireNonNull(summary, "summary cannot be null");
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
