package io.meterforge.controlplane.credential.api;

import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.credential.api.dto.CreateCredentialRequest;
import io.meterforge.controlplane.credential.api.dto.CreateCredentialResponse;
import io.meterforge.controlplane.credential.api.dto.CredentialResponse;
import io.meterforge.controlplane.credential.application.CredentialService;
import io.meterforge.controlplane.credential.domain.ApiCredential;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class CredentialController {

    private final CredentialService credentialService;
    private final WorkspaceSecurityEvaluator securityEvaluator;

    public CredentialController(CredentialService credentialService, WorkspaceSecurityEvaluator securityEvaluator) {
        this.credentialService = credentialService;
        this.securityEvaluator = securityEvaluator;
    }

    @GetMapping("/applications/{applicationId}/credentials")
    public ResponseEntity<List<CredentialResponse>> listCredentials(
            @PathVariable UUID workspaceId,
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        List<CredentialResponse> responses = credentialService.listCredentials(workspaceId, applicationId).stream()
                .map(CredentialResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/applications/{applicationId}/credentials")
    public ResponseEntity<CreateCredentialResponse> issueCredential(
            @PathVariable UUID workspaceId,
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestBody(required = false) CreateCredentialRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        String env = request != null ? request.environment() : "dev";
        var result = credentialService.issueCredential(
                workspaceId,
                principal.userId(),
                applicationId,
                env,
                request != null ? request.expiresAt() : null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateCredentialResponse.from(result.credential(), result.rawKey()));
    }

    @PostMapping("/credentials/{credentialId}/revoke")
    public ResponseEntity<CredentialResponse> revokeCredential(
            @PathVariable UUID workspaceId,
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        ApiCredential credential = credentialService.revokeCredential(workspaceId, principal.userId(), credentialId);
        return ResponseEntity.ok(CredentialResponse.from(credential));
    }

    @PostMapping("/credentials/{credentialId}/rotate")
    public ResponseEntity<CreateCredentialResponse> rotateCredential(
            @PathVariable UUID workspaceId,
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        var result = credentialService.rotateCredential(workspaceId, principal.userId(), credentialId);
        return ResponseEntity.ok(CreateCredentialResponse.from(result.credential(), result.rawKey()));
    }
}
