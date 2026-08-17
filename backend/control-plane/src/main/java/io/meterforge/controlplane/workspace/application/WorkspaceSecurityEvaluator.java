package io.meterforge.controlplane.workspace.application;

import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.common.exception.ForbiddenException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.workspace.domain.WorkspaceMember;
import io.meterforge.controlplane.workspace.domain.WorkspaceMemberRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkspaceSecurityEvaluator {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceSecurityEvaluator(WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    public WorkspaceMember requireMembership(UUID workspaceId, UUID userId) {
        if (userId == null) {
            throw new ForbiddenException("Authentication required");
        }
        return workspaceMemberRepository.findByIdWorkspaceIdAndIdUserId(workspaceId, userId)
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));
    }

    public WorkspaceMember requireRole(UUID workspaceId, UUID userId, Role minimumRole) {
        WorkspaceMember member = requireMembership(workspaceId, userId);
        if (!hasSufficientRole(member.getRole(), minimumRole)) {
            throw new ForbiddenException("Insufficient permissions. Required role: " + minimumRole);
        }
        return member;
    }

    private boolean hasSufficientRole(Role actual, Role minimum) {
        if (actual == Role.OWNER) {
            return true;
        }
        if (actual == Role.MEMBER) {
            return minimum == Role.MEMBER || minimum == Role.VIEWER;
        }
        if (actual == Role.VIEWER) {
            return minimum == Role.VIEWER;
        }
        return false;
    }
}
