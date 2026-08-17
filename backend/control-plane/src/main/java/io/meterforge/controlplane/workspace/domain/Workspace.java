package io.meterforge.controlplane.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workspaces", schema = "meterforge")
public class Workspace {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Workspace() {}

    public Workspace(UUID id, String name, String slug, String status) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null").trim();
        this.slug = Objects.requireNonNull(slug, "slug cannot be null").trim().toLowerCase();
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getStatus() {
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

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null").trim();
        this.updatedAt = Instant.now();
    }

    public void setStatus(String status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workspace workspace)) return false;
        return Objects.equals(id, workspace.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
