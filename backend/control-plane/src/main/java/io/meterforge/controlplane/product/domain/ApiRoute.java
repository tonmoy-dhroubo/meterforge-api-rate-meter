package io.meterforge.controlplane.product.domain;

import io.meterforge.contracts.common.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "api_routes", schema = "meterforge")
public class ApiRoute {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Column(name = "upstream_path")
    private String upstreamPath;

    @Column(name = "cost_units", nullable = false)
    private int costUnits;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ResourceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ApiRoute() {}

    public ApiRoute(
            UUID id,
            UUID workspaceId,
            UUID productId,
            String httpMethod,
            String pathPattern,
            String upstreamPath,
            int costUnits,
            int priority,
            ResourceStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId cannot be null");
        this.productId = Objects.requireNonNull(productId, "productId cannot be null");
        this.httpMethod = Objects.requireNonNull(httpMethod, "httpMethod cannot be null").trim().toUpperCase();
        this.pathPattern = Objects.requireNonNull(pathPattern, "pathPattern cannot be null").trim();
        this.upstreamPath = upstreamPath != null && !upstreamPath.isBlank() ? upstreamPath.trim() : null;
        if (costUnits < 1) {
            throw new IllegalArgumentException("costUnits must be >= 1");
        }
        this.costUnits = costUnits;
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be >= 0");
        }
        this.priority = priority;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public String getUpstreamPath() {
        return upstreamPath;
    }

    public int getCostUnits() {
        return costUnits;
    }

    public int getPriority() {
        return priority;
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void update(String upstreamPath, Integer costUnits, Integer priority) {
        if (upstreamPath != null) {
            this.upstreamPath = upstreamPath.isBlank() ? null : upstreamPath.trim();
        }
        if (costUnits != null) {
            if (costUnits < 1) {
                throw new IllegalArgumentException("costUnits must be >= 1");
            }
            this.costUnits = costUnits;
        }
        if (priority != null) {
            if (priority < 0) {
                throw new IllegalArgumentException("priority must be >= 0");
            }
            this.priority = priority;
        }
        this.updatedAt = Instant.now();
    }

    public void setStatus(ResourceStatus status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiRoute that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
