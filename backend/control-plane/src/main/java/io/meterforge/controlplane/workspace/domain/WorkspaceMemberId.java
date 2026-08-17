package io.meterforge.controlplane.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WorkspaceMemberId implements Serializable {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected WorkspaceMemberId() {}

    public WorkspaceMemberId(UUID workspaceId, UUID userId) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceMemberId that)) return false;
        return Objects.equals(workspaceId, that.workspaceId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, userId);
    }
}
