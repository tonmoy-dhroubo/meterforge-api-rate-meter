package io.meterforge.controlplane.workspace.api;

import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.workspace.api.dto.AddWorkspaceMemberRequest;
import io.meterforge.controlplane.workspace.api.dto.UpdateWorkspaceMemberRequest;
import io.meterforge.controlplane.workspace.api.dto.WorkspaceMemberResponse;
import io.meterforge.controlplane.workspace.application.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private final WorkspaceService workspaceService;

    public WorkspaceMemberController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceMemberResponse>> listMembers(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        List<WorkspaceMemberResponse> members = workspaceService.listMembers(workspaceId, principal.userId());
        return ResponseEntity.ok(members);
    }

    @PostMapping
    public ResponseEntity<WorkspaceMemberResponse> addMember(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        WorkspaceMemberResponse response = workspaceService.addMember(workspaceId, request, principal.userId(), requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<WorkspaceMemberResponse> updateMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @RequestBody UpdateWorkspaceMemberRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        WorkspaceMemberResponse response = workspaceService.updateMember(workspaceId, userId, request, principal.userId(), requestId);
        return ResponseEntity.ok(response);
    }

    private void requireAuth(StaffPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
