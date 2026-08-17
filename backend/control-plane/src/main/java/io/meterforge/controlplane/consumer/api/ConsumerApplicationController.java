package io.meterforge.controlplane.consumer.api;

import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.consumer.api.dto.ApplicationResponse;
import io.meterforge.controlplane.consumer.api.dto.CreateApplicationRequest;
import io.meterforge.controlplane.consumer.api.dto.UpdateApplicationRequest;
import io.meterforge.controlplane.consumer.application.ConsumerApplicationService;
import io.meterforge.controlplane.consumer.domain.ConsumerApplication;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class ConsumerApplicationController {

    private final ConsumerApplicationService applicationService;
    private final WorkspaceSecurityEvaluator securityEvaluator;

    public ConsumerApplicationController(ConsumerApplicationService applicationService,
                                         WorkspaceSecurityEvaluator securityEvaluator) {
        this.applicationService = applicationService;
        this.securityEvaluator = securityEvaluator;
    }

    @GetMapping("/consumers/{consumerId}/applications")
    public ResponseEntity<List<ApplicationResponse>> listApplicationsByConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        List<ApplicationResponse> responses = applicationService.listApplications(workspaceId, consumerId).stream()
                .map(ApplicationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> listAllApplications(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        List<ApplicationResponse> responses = applicationService.listAllWorkspaceApplications(workspaceId).stream()
                .map(ApplicationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApplicationResponse> getApplication(
            @PathVariable UUID workspaceId,
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        ConsumerApplication app = applicationService.getApplication(workspaceId, applicationId);
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/consumers/{consumerId}/applications")
    public ResponseEntity<ApplicationResponse> createApplication(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateApplicationRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        ConsumerApplication app = applicationService.createApplication(
                workspaceId,
                principal.userId(),
                consumerId,
                request.name()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(app));
    }

    @PatchMapping("/applications/{applicationId}")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable UUID workspaceId,
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody UpdateApplicationRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        ConsumerApplication app = applicationService.updateApplication(
                workspaceId,
                principal.userId(),
                applicationId,
                request.name()
        );
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/applications/{applicationId}/activate")
    public ResponseEntity<ApplicationResponse> activateApplication(
            @PathVariable UUID workspaceId,
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        ConsumerApplication app = applicationService.activateApplication(
                workspaceId,
                principal.userId(),
                applicationId
        );
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/applications/{applicationId}/disable")
    public ResponseEntity<ApplicationResponse> disableApplication(
            @PathVariable UUID workspaceId,
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        ConsumerApplication app = applicationService.disableApplication(
                workspaceId,
                principal.userId(),
                applicationId
        );
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }
}
