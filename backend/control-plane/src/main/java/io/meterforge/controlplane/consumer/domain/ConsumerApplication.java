package io.meterforge.controlplane.consumer.domain;

import io.meterforge.contracts.common.ResourceStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumer_applications", schema = "meterforge")
public class ConsumerApplication {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ResourceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ConsumerApplication() {}

    public ConsumerApplication(UUID id, UUID workspaceId, UUID consumerId, String name) {
        this.id = id != null ? id : UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.consumerId = consumerId;
        this.name = name;
        this.status = ResourceStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getConsumerId() { return consumerId; }
    public String getName() { return name; }
    public ResourceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void update(String name) {
        if (name != null && !name.isBlank()) this.name = name.trim();
        this.updatedAt = Instant.now();
    }

    public void setStatus(ResourceStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
