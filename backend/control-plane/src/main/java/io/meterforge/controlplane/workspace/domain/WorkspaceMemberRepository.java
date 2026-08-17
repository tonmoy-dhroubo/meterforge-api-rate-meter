package io.meterforge.controlplane.workspace.domain;

import io.meterforge.contracts.common.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {
    List<WorkspaceMember> findByIdUserId(UUID userId);
    List<WorkspaceMember> findByIdWorkspaceId(UUID workspaceId);
    Optional<WorkspaceMember> findByIdWorkspaceIdAndIdUserId(UUID workspaceId, UUID userId);
    long countByIdWorkspaceIdAndRoleAndStatus(UUID workspaceId, Role role, String status);
}
