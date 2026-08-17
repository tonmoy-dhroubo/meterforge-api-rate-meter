package io.meterforge.controlplane.subscription.domain;

import io.meterforge.contracts.common.ResourceStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", schema = "meterforge")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ResourceStatus status;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Subscription() {}

    public Subscription(UUID id, UUID workspaceId, UUID applicationId, UUID productId, UUID planId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.applicationId = applicationId;
        this.productId = productId;
        this.planId = planId;
        this.status = ResourceStatus.ACTIVE;
        this.effectiveFrom = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getProductId() { return productId; }
    public UUID getPlanId() { return planId; }
    public ResourceStatus getStatus() { return status; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void cancel() {
        this.status = ResourceStatus.DISABLED;
        this.effectiveTo = Instant.now();
        this.updatedAt = Instant.now();
    }
}
