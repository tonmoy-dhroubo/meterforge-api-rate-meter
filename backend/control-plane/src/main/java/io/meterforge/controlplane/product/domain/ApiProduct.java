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
@Table(name = "api_products", schema = "meterforge")
public class ApiProduct {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "upstream_base_url", nullable = false)
    private String upstreamBaseUrl;

    @Column(name = "gateway_base_path", nullable = false)
    private String gatewayBasePath;

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

    protected ApiProduct() {}

    public ApiProduct(
            UUID id,
            UUID workspaceId,
            String name,
            String slug,
            String upstreamBaseUrl,
            String gatewayBasePath,
            ResourceStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null").trim();
        this.slug = Objects.requireNonNull(slug, "slug cannot be null").trim().toLowerCase();
        this.upstreamBaseUrl = Objects.requireNonNull(upstreamBaseUrl, "upstreamBaseUrl cannot be null").trim();
        this.gatewayBasePath = normalizeBasePath(gatewayBasePath);
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    public static String normalizeBasePath(String path) {
        Objects.requireNonNull(path, "gatewayBasePath cannot be null");
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getUpstreamBaseUrl() {
        return upstreamBaseUrl;
    }

    public String getGatewayBasePath() {
        return gatewayBasePath;
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

    public void update(String name, String upstreamBaseUrl, String gatewayBasePath) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (upstreamBaseUrl != null && !upstreamBaseUrl.isBlank()) {
            this.upstreamBaseUrl = upstreamBaseUrl.trim();
        }
        if (gatewayBasePath != null && !gatewayBasePath.isBlank()) {
            this.gatewayBasePath = normalizeBasePath(gatewayBasePath);
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
        if (!(o instanceof ApiProduct that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
