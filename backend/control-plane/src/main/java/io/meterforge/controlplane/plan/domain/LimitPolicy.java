package io.meterforge.controlplane.plan.domain;

import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "limit_policies", schema = "meterforge")
public class LimitPolicy {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "route_id")
    private UUID routeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private LimitPolicyKind kind;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "refill_tokens")
    private Integer refillTokens;

    @Column(name = "refill_period_seconds")
    private Integer refillPeriodSeconds;

    @Column(name = "quota_limit")
    private Long quotaLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_period")
    private QuotaPeriod quotaPeriod;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LimitPolicy() {}

    public static LimitPolicy createRatePolicy(UUID id, UUID workspaceId, UUID planId, UUID routeId,
                                               int capacity, int refillTokens, int refillPeriodSeconds) {
        LimitPolicy policy = new LimitPolicy();
        policy.id = id != null ? id : UUID.randomUUID();
        policy.workspaceId = workspaceId;
        policy.planId = planId;
        policy.routeId = routeId;
        policy.kind = LimitPolicyKind.RATE;
        policy.capacity = capacity;
        policy.refillTokens = refillTokens;
        policy.refillPeriodSeconds = refillPeriodSeconds;
        policy.enabled = true;
        policy.createdAt = Instant.now();
        policy.updatedAt = Instant.now();
        policy.version = 0;
        return policy;
    }

    public static LimitPolicy createQuotaPolicy(UUID id, UUID workspaceId, UUID planId, UUID routeId,
                                                long quotaLimit, QuotaPeriod quotaPeriod) {
        LimitPolicy policy = new LimitPolicy();
        policy.id = id != null ? id : UUID.randomUUID();
        policy.workspaceId = workspaceId;
        policy.planId = planId;
        policy.routeId = routeId;
        policy.kind = LimitPolicyKind.QUOTA;
        policy.quotaLimit = quotaLimit;
        policy.quotaPeriod = quotaPeriod;
        policy.enabled = true;
        policy.createdAt = Instant.now();
        policy.updatedAt = Instant.now();
        policy.version = 0;
        return policy;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getPlanId() { return planId; }
    public UUID getRouteId() { return routeId; }
    public LimitPolicyKind getKind() { return kind; }
    public Integer getCapacity() { return capacity; }
    public Integer getRefillTokens() { return refillTokens; }
    public Integer getRefillPeriodSeconds() { return refillPeriodSeconds; }
    public Long getQuotaLimit() { return quotaLimit; }
    public QuotaPeriod getQuotaPeriod() { return quotaPeriod; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
