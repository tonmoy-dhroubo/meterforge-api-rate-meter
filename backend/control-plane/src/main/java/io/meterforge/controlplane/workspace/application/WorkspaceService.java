package io.meterforge.controlplane.workspace.application;

import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.InvalidInputException;
import io.meterforge.controlplane.common.exception.ResourceConflictException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.identity.domain.User;
import io.meterforge.controlplane.identity.domain.UserRepository;
import io.meterforge.controlplane.workspace.api.dto.AddWorkspaceMemberRequest;
import io.meterforge.controlplane.workspace.api.dto.CreateWorkspaceRequest;
import io.meterforge.controlplane.workspace.api.dto.UpdateWorkspaceMemberRequest;
import io.meterforge.controlplane.workspace.api.dto.UpdateWorkspaceRequest;
import io.meterforge.controlplane.workspace.api.dto.WorkspaceMemberResponse;
import io.meterforge.controlplane.workspace.api.dto.WorkspaceResponse;
import io.meterforge.controlplane.workspace.domain.Workspace;
import io.meterforge.controlplane.workspace.domain.WorkspaceMember;
import io.meterforge.controlplane.workspace.domain.WorkspaceMemberId;
import io.meterforge.controlplane.workspace.domain.WorkspaceMemberRepository;
import io.meterforge.controlplane.workspace.domain.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceSecurityEvaluator securityEvaluator;
    private final TransactionalMutationService mutationService;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            WorkspaceSecurityEvaluator securityEvaluator,
            TransactionalMutationService mutationService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.securityEvaluator = securityEvaluator;
        this.mutationService = mutationService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listUserWorkspaces(UUID userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByIdUserId(userId);
        List<UUID> workspaceIds = memberships.stream()
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .map(m -> m.getId().getWorkspaceId())
                .toList();

        Map<UUID, WorkspaceMember> memberMap = memberships.stream()
                .collect(Collectors.toMap(m -> m.getId().getWorkspaceId(), m -> m, (a, b) -> a));

        return workspaceRepository.findAllById(workspaceIds).stream()
                .filter(w -> "ACTIVE".equalsIgnoreCase(w.getStatus()))
                .map(w -> mapToResponse(w, memberMap.get(w.getId()).getRole()))
                .toList();
    }

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID userId, String requestId) {
        String slug = request.slug().trim().toLowerCase();
        if (workspaceRepository.existsBySlug(slug)) {
            throw new ResourceConflictException("Workspace with slug '" + slug + "' already exists");
        }

        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, request.name(), slug, "ACTIVE");
        workspaceRepository.save(workspace);

        WorkspaceMember member = new WorkspaceMember(
                new WorkspaceMemberId(workspaceId, userId),
                Role.OWNER,
                "ACTIVE"
        );
        workspaceMemberRepository.save(member);

        mutationService.recordMutation(
                workspaceId,
                userId,
                "WORKSPACE_CREATED",
                "WORKSPACE",
                workspaceId,
                requestId,
                "Workspace '" + workspace.getName() + "' created with slug '" + slug + "'",
                Map.of("name", workspace.getName(), "slug", slug),
                null
        );

        return mapToResponse(workspace, Role.OWNER);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID workspaceId, UUID userId) {
        WorkspaceMember member = securityEvaluator.requireMembership(workspaceId, userId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        return mapToResponse(workspace, member.getRole());
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspaceBySlug(String slug, UUID userId) {
        Workspace workspace = workspaceRepository.findBySlug(slug.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        WorkspaceMember member = securityEvaluator.requireMembership(workspace.getId(), userId);
        return mapToResponse(workspace, member.getRole());
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID userId, String requestId) {
        WorkspaceMember member = securityEvaluator.requireRole(workspaceId, userId, Role.OWNER);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        if (request.name() != null && !request.name().isBlank()) {
            workspace.setName(request.name());
        }
        if (request.status() != null && !request.status().isBlank()) {
            workspace.setStatus(request.status().trim().toUpperCase());
        }
        workspaceRepository.save(workspace);

        mutationService.recordMutation(
                workspaceId,
                userId,
                "WORKSPACE_UPDATED",
                "WORKSPACE",
                workspaceId,
                requestId,
                "Workspace '" + workspace.getName() + "' updated",
                Map.of("name", workspace.getName(), "status", workspace.getStatus()),
                null
        );

        return mapToResponse(workspace, member.getRole());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(UUID workspaceId, UUID userId) {
        securityEvaluator.requireMembership(workspaceId, userId);
        List<WorkspaceMember> members = workspaceMemberRepository.findByIdWorkspaceId(workspaceId);
        List<UUID> userIds = members.stream().map(m -> m.getId().getUserId()).toList();
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<WorkspaceMemberResponse> responses = new ArrayList<>();
        for (WorkspaceMember member : members) {
            User u = userMap.get(member.getId().getUserId());
            String email = u != null ? u.getEmail() : "unknown";
            responses.add(new WorkspaceMemberResponse(
                    member.getId().getUserId(),
                    email,
                    member.getRole(),
                    member.getStatus(),
                    member.getCreatedAt(),
                    member.getUpdatedAt()
            ));
        }
        return responses;
    }

    @Transactional
    public WorkspaceMemberResponse addMember(UUID workspaceId, AddWorkspaceMemberRequest request, UUID actorUserId, String requestId) {
        securityEvaluator.requireRole(workspaceId, actorUserId, Role.OWNER);

        String email = request.email().trim().toLowerCase();
        User targetUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email '" + email + "'"));

        WorkspaceMemberId memberId = new WorkspaceMemberId(workspaceId, targetUser.getId());
        if (workspaceMemberRepository.existsById(memberId)) {
            throw new ResourceConflictException("User is already a member of this workspace");
        }

        WorkspaceMember member = new WorkspaceMember(memberId, request.role(), "ACTIVE");
        workspaceMemberRepository.save(member);

        mutationService.recordMutation(
                workspaceId,
                actorUserId,
                "WORKSPACE_MEMBER_ADDED",
                "WORKSPACE_MEMBER",
                targetUser.getId(),
                requestId,
                "User '" + email + "' added as " + request.role(),
                Map.of("userId", targetUser.getId(), "email", email, "role", request.role()),
                null
        );

        return new WorkspaceMemberResponse(
                targetUser.getId(),
                targetUser.getEmail(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    @Transactional
    public WorkspaceMemberResponse updateMember(
            UUID workspaceId,
            UUID targetUserId,
            UpdateWorkspaceMemberRequest request,
            UUID actorUserId,
            String requestId
    ) {
        securityEvaluator.requireRole(workspaceId, actorUserId, Role.OWNER);

        WorkspaceMemberId memberId = new WorkspaceMemberId(workspaceId, targetUserId);
        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace member not found"));

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Enforce invariant: Workspace must have at least one active OWNER
        if (member.getRole() == Role.OWNER && (request.role() != null && request.role() != Role.OWNER || request.status() != null && !"ACTIVE".equalsIgnoreCase(request.status()))) {
            long activeOwners = workspaceMemberRepository.countByIdWorkspaceIdAndRoleAndStatus(workspaceId, Role.OWNER, "ACTIVE");
            if (activeOwners <= 1) {
                throw new InvalidInputException("Cannot demote or deactivate the last active OWNER of the workspace", "LAST_OWNER_PROTECTION");
            }
        }

        if (request.role() != null) {
            member.setRole(request.role());
        }
        if (request.status() != null && !request.status().isBlank()) {
            member.setStatus(request.status().trim().toUpperCase());
        }
        workspaceMemberRepository.save(member);

        mutationService.recordMutation(
                workspaceId,
                actorUserId,
                "WORKSPACE_MEMBER_UPDATED",
                "WORKSPACE_MEMBER",
                targetUserId,
                requestId,
                "Member '" + targetUser.getEmail() + "' updated to role " + member.getRole() + " and status " + member.getStatus(),
                Map.of("userId", targetUserId, "role", member.getRole(), "status", member.getStatus()),
                null
        );

        return new WorkspaceMemberResponse(
                targetUser.getId(),
                targetUser.getEmail(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    private WorkspaceResponse mapToResponse(Workspace w, Role role) {
        return new WorkspaceResponse(
                w.getId(),
                w.getName(),
                w.getSlug(),
                w.getStatus(),
                role,
                w.getCreatedAt(),
                w.getUpdatedAt(),
                w.getVersion()
        );
    }
}
