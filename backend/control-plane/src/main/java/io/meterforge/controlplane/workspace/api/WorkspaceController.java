package io.meterforge.controlplane.workspace.api;

import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.workspace.api.dto.CreateWorkspaceRequest;
import io.meterforge.controlplane.workspace.api.dto.UpdateWorkspaceRequest;
import io.meterforge.controlplane.workspace.api.dto.WorkspaceResponse;
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
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> listWorkspaces(@AuthenticationPrincipal StaffPrincipal principal) {
        requireAuth(principal);
        List<WorkspaceResponse> workspaces = workspaceService.listUserWorkspaces(principal.userId());
        return ResponseEntity.ok(workspaces);
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        WorkspaceResponse workspace = workspaceService.createWorkspace(request, principal.userId(), requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(workspace);
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        WorkspaceResponse workspace = workspaceService.getWorkspace(workspaceId, principal.userId());
        return ResponseEntity.ok(workspace);
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<WorkspaceResponse> getWorkspaceBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal StaffPrincipal principal
    ) {
        requireAuth(principal);
        WorkspaceResponse workspace = workspaceService.getWorkspaceBySlug(slug, principal.userId());
        return ResponseEntity.ok(workspace);
    }

    @PatchMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> updateWorkspace(
            @PathVariable UUID workspaceId,
            @RequestBody UpdateWorkspaceRequest request,
            @AuthenticationPrincipal StaffPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        requireAuth(principal);
        String requestId = resolveRequestId(servletRequest);
        WorkspaceResponse workspace = workspaceService.updateWorkspace(workspaceId, request, principal.userId(), requestId);
        return ResponseEntity.ok(workspace);
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
