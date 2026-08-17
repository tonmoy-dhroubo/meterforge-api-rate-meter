package io.meterforge.controlplane.credential.domain;

import io.meterforge.contracts.common.ResourceStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_credentials", schema = "meterforge")
public class ApiCredential {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "secret_hmac", nullable = false)
    private String secretHmac;

    @Column(name = "display_prefix", nullable = false, length = 32)
    private String displayPrefix;

    @Column(name = "display_last_four", nullable = false, length = 16)
    private String displayLastFour;

    @Column(name = "environment", nullable = false, length = 32)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ResourceStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ApiCredential() {}

    public ApiCredential(UUID id, UUID workspaceId, UUID applicationId, String publicId,
                         String secretHmac, String displayPrefix, String displayLastFour,
                         String environment, Instant expiresAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.applicationId = applicationId;
        this.publicId = publicId;
        this.secretHmac = secretHmac;
        this.displayPrefix = displayPrefix;
        this.displayLastFour = displayLastFour;
        this.environment = environment != null ? environment : "dev";
        this.status = ResourceStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getApplicationId() { return applicationId; }
    public String getPublicId() { return publicId; }
    public String getSecretHmac() { return secretHmac; }
    public String getDisplayPrefix() { return displayPrefix; }
    public String getDisplayLastFour() { return displayLastFour; }
    public String getEnvironment() { return environment; }
    public ResourceStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void revoke() {
        this.status = ResourceStatus.DISABLED;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
