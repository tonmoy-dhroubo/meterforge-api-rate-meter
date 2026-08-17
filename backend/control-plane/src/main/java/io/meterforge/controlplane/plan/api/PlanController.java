package io.meterforge.controlplane.plan.api;

import io.meterforge.contracts.common.Role;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import io.meterforge.controlplane.plan.api.dto.CreateLimitPolicyRequest;
import io.meterforge.controlplane.plan.api.dto.CreatePlanRequest;
import io.meterforge.controlplane.plan.api.dto.LimitPolicyResponse;
import io.meterforge.controlplane.plan.api.dto.PlanResponse;
import io.meterforge.controlplane.plan.application.PlanService;
import io.meterforge.controlplane.plan.domain.LimitPolicy;
import io.meterforge.controlplane.plan.domain.Plan;
import io.meterforge.controlplane.workspace.application.WorkspaceSecurityEvaluator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/plans")
public class PlanController {

    private final PlanService planService;
    private final WorkspaceSecurityEvaluator securityEvaluator;

    public PlanController(PlanService planService, WorkspaceSecurityEvaluator securityEvaluator) {
        this.planService = planService;
        this.securityEvaluator = securityEvaluator;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> listPlans(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID productId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        List<PlanResponse> responses = planService.listPlans(workspaceId, productId).stream()
                .map(plan -> {
                    List<LimitPolicyResponse> policies = planService.getPlanPolicies(workspaceId, plan.getId()).stream()
                            .map(LimitPolicyResponse::from)
                            .toList();
                    return PlanResponse.from(plan, policies);
                })
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{planId}")
    public ResponseEntity<PlanResponse> getPlan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.VIEWER);
        Plan plan = planService.getPlan(workspaceId, planId);
        List<LimitPolicyResponse> policies = planService.getPlanPolicies(workspaceId, planId).stream()
                .map(LimitPolicyResponse::from)
                .toList();
        return ResponseEntity.ok(PlanResponse.from(plan, policies));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreatePlanRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);

        List<LimitPolicy> initialPolicies = new ArrayList<>();
        if (request.policies() != null) {
            for (CreateLimitPolicyRequest pReq : request.policies()) {
                if (pReq.kind() == io.meterforge.contracts.common.LimitPolicyKind.RATE) {
                    initialPolicies.add(LimitPolicy.createRatePolicy(
                            null, workspaceId, null, pReq.routeId(),
                            pReq.capacity() != null ? pReq.capacity() : 10,
                            pReq.refillTokens() != null ? pReq.refillTokens() : 10,
                            pReq.refillPeriodSeconds() != null ? pReq.refillPeriodSeconds() : 1
                    ));
                } else {
                    initialPolicies.add(LimitPolicy.createQuotaPolicy(
                            null, workspaceId, null, pReq.routeId(),
                            pReq.quotaLimit() != null ? pReq.quotaLimit() : 1000,
                            pReq.quotaPeriod() != null ? pReq.quotaPeriod() : io.meterforge.contracts.common.QuotaPeriod.DAY
                    ));
                }
            }
        }

        Plan plan = planService.createPlan(
                workspaceId,
                principal.userId(),
                request.productId(),
                request.name(),
                request.slug(),
                initialPolicies
        );

        List<LimitPolicyResponse> policies = planService.getPlanPolicies(workspaceId, plan.getId()).stream()
                .map(LimitPolicyResponse::from)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanResponse.from(plan, policies));
    }

    @PostMapping("/{planId}/policies")
    public ResponseEntity<LimitPolicyResponse> addPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateLimitPolicyRequest request) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);

        LimitPolicy policy;
        if (request.kind() == io.meterforge.contracts.common.LimitPolicyKind.RATE) {
            policy = LimitPolicy.createRatePolicy(
                    null, workspaceId, planId, request.routeId(),
                    request.capacity() != null ? request.capacity() : 10,
                    request.refillTokens() != null ? request.refillTokens() : 10,
                    request.refillPeriodSeconds() != null ? request.refillPeriodSeconds() : 1
            );
        } else {
            policy = LimitPolicy.createQuotaPolicy(
                    null, workspaceId, planId, request.routeId(),
                    request.quotaLimit() != null ? request.quotaLimit() : 1000,
                    request.quotaPeriod() != null ? request.quotaPeriod() : io.meterforge.contracts.common.QuotaPeriod.DAY
            );
        }

        LimitPolicy saved = planService.addPolicyToPlan(workspaceId, principal.userId(), planId, policy);
        return ResponseEntity.status(HttpStatus.CREATED).body(LimitPolicyResponse.from(saved));
    }

    @PatchMapping("/{planId}/policies/{policyId}")
    public ResponseEntity<LimitPolicyResponse> togglePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @PathVariable UUID policyId,
            @RequestParam boolean enabled,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        LimitPolicy policy = planService.togglePolicy(workspaceId, principal.userId(), planId, policyId, enabled);
        return ResponseEntity.ok(LimitPolicyResponse.from(policy));
    }

    @PostMapping("/{planId}/activate")
    public ResponseEntity<PlanResponse> activatePlan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Plan plan = planService.activatePlan(workspaceId, principal.userId(), planId);
        List<LimitPolicyResponse> policies = planService.getPlanPolicies(workspaceId, planId).stream()
                .map(LimitPolicyResponse::from)
                .toList();
        return ResponseEntity.ok(PlanResponse.from(plan, policies));
    }

    @PostMapping("/{planId}/disable")
    public ResponseEntity<PlanResponse> disablePlan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @AuthenticationPrincipal StaffPrincipal principal) {
        securityEvaluator.requireRole(workspaceId, principal.userId(), Role.MEMBER);
        Plan plan = planService.disablePlan(workspaceId, principal.userId(), planId);
        List<LimitPolicyResponse> policies = planService.getPlanPolicies(workspaceId, planId).stream()
                .map(LimitPolicyResponse::from)
                .toList();
        return ResponseEntity.ok(PlanResponse.from(plan, policies));
    }
}
