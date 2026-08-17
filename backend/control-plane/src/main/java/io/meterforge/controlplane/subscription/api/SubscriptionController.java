package io.meterforge.controlplane.subscription.api;

import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.subscription.api.dto.CreateSubscriptionRequest;
import io.meterforge.controlplane.subscription.api.dto.SubscriptionResponse;
import io.meterforge.controlplane.subscription.application.SubscriptionService;
import io.meterforge.controlplane.subscription.domain.Subscription;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final WorkspaceSecurityEvaluator securityEvaluator;

    public SubscriptionController(SubscriptionService subscriptionService, WorkspaceSecurityEvaluator securityEvaluator) {
        this.subscriptionService = subscriptionService;
        this.securityEvaluator = securityEvaluator;
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> listSubscriptions(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID applicationId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        List<SubscriptionResponse> responses = subscriptionService.listSubscriptions(workspaceId, applicationId).stream()
                .map(SubscriptionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        Subscription sub = subscriptionService.getSubscription(workspaceId, subscriptionId);
        return ResponseEntity.ok(SubscriptionResponse.from(sub));
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Subscription sub = subscriptionService.createSubscription(
                workspaceId,
                principal.userId(),
                request.applicationId(),
                request.productId(),
                request.planId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(sub));
    }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Subscription sub = subscriptionService.cancelSubscription(workspaceId, principal.userId(), subscriptionId);
        return ResponseEntity.ok(SubscriptionResponse.from(sub));
    }
}
