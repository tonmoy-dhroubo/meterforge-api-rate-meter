package io.meterforge.controlplane.workspace.domain;

import io.meterforge.contracts.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "workspace_members", schema = "meterforge")
public class WorkspaceMember {

    @EmbeddedId
    private WorkspaceMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceMember() {}

    public WorkspaceMember(WorkspaceMemberId id, Role role, String status) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public WorkspaceMemberId getId() {
        return id;
    }

    public Role getRole() {
        return role;
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

    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.updatedAt = Instant.now();
    }

    public void setStatus(String status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceMember that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
