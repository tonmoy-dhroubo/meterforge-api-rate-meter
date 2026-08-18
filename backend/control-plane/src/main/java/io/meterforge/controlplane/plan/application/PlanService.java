package io.meterforge.controlplane.plan.application;

import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.LimitPolicyDto;
import io.meterforge.contracts.event.PlanConfigurationChangedV1;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.InvalidInputException;
import io.meterforge.controlplane.common.exception.ResourceConflictException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.plan.domain.LimitPolicy;
import io.meterforge.controlplane.plan.domain.LimitPolicyRepository;
import io.meterforge.controlplane.plan.domain.Plan;
import io.meterforge.controlplane.plan.domain.PlanRepository;
import io.meterforge.controlplane.product.domain.ApiProductRepository;
import io.meterforge.controlplane.product.domain.ApiRouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final LimitPolicyRepository policyRepository;
    private final ApiProductRepository productRepository;
    private final ApiRouteRepository routeRepository;
    private final TransactionalMutationService mutationService;

    public PlanService(PlanRepository planRepository,
                       LimitPolicyRepository policyRepository,
                       ApiProductRepository productRepository,
                       ApiRouteRepository routeRepository,
                       TransactionalMutationService mutationService) {
        this.planRepository = planRepository;
        this.policyRepository = policyRepository;
        this.productRepository = productRepository;
        this.routeRepository = routeRepository;
        this.mutationService = mutationService;
    }

    @Transactional(readOnly = true)
    public List<Plan> listPlans(UUID workspaceId, UUID productId) {
        if (productId != null) {
            return planRepository.findByWorkspaceIdAndProductIdOrderByCreatedAtDesc(workspaceId, productId);
        }
        return planRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Plan getPlan(UUID workspaceId, UUID planId) {
        return planRepository.findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
    }

    @Transactional(readOnly = true)
    public List<LimitPolicy> getPlanPolicies(UUID workspaceId, UUID planId) {
        getPlan(workspaceId, planId);
        return policyRepository.findByWorkspaceIdAndPlanIdOrderByCreatedAtAsc(workspaceId, planId);
    }

    @Transactional
    public Plan createPlan(UUID workspaceId, UUID userId, UUID productId, String name, String slug, List<LimitPolicy> policies) {
        productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (planRepository.existsByWorkspaceIdAndProductIdAndSlug(workspaceId, productId, slug)) {
            throw new ResourceConflictException("Plan with slug '" + slug + "' already exists for this product");
        }

        Plan plan = new Plan(null, workspaceId, productId, name.trim(), slug.trim().toLowerCase());
        plan = planRepository.saveAndFlush(plan);

        List<LimitPolicy> savedPolicies = new ArrayList<>();
        if (policies != null) {
            for (LimitPolicy policy : policies) {
                validatePolicy(policy);
                if (policy.getRouteId() != null) {
                    routeRepository.findByIdAndWorkspaceIdAndProductId(policy.getRouteId(), workspaceId, productId)
                            .orElseThrow(() -> new InvalidInputException("Route does not belong to the plan's product"));
                }
                LimitPolicy toSave;
                if (policy.getKind() == LimitPolicyKind.RATE) {
                    toSave = LimitPolicy.createRatePolicy(
                            null, workspaceId, plan.getId(), policy.getRouteId(),
                            policy.getCapacity(), policy.getRefillTokens(), policy.getRefillPeriodSeconds()
                    );
                } else {
                    toSave = LimitPolicy.createQuotaPolicy(
                            null, workspaceId, plan.getId(), policy.getRouteId(),
                            policy.getQuotaLimit(), policy.getQuotaPeriod()
                    );
                }
                savedPolicies.add(policyRepository.saveAndFlush(toSave));
            }
        }

        emitPlanChangedEvent(plan, savedPolicies, userId, "CREATE_PLAN", "Created plan '" + plan.getName() + "'");
        return plan;
    }

    @Transactional
    public LimitPolicy addPolicyToPlan(UUID workspaceId, UUID userId, UUID planId, LimitPolicy policy) {
        Plan plan = getPlan(workspaceId, planId);
        validatePolicy(policy);

        if (policy.getRouteId() != null) {
            routeRepository.findByIdAndWorkspaceIdAndProductId(policy.getRouteId(), workspaceId, plan.getProductId())
                    .orElseThrow(() -> new InvalidInputException("Route does not belong to the plan's product"));
        }

        LimitPolicy toSave;
        if (policy.getKind() == LimitPolicyKind.RATE) {
            toSave = LimitPolicy.createRatePolicy(
                    null, workspaceId, planId, policy.getRouteId(),
                    policy.getCapacity(), policy.getRefillTokens(), policy.getRefillPeriodSeconds()
            );
        } else {
            toSave = LimitPolicy.createQuotaPolicy(
                    null, workspaceId, planId, policy.getRouteId(),
                    policy.getQuotaLimit(), policy.getQuotaPeriod()
                    );
        }
        LimitPolicy saved = policyRepository.saveAndFlush(toSave);

        plan.markUpdated();
        plan = planRepository.saveAndFlush(plan);

        List<LimitPolicy> allPolicies = policyRepository.findByWorkspaceIdAndPlanIdOrderByCreatedAtAsc(workspaceId, planId);
        emitPlanChangedEvent(plan, allPolicies, userId, "ADD_LIMIT_POLICY", "Added limit policy to plan '" + plan.getName() + "'");

        return saved;
    }

    @Transactional
    public LimitPolicy togglePolicy(UUID workspaceId, UUID userId, UUID planId, UUID policyId, boolean enabled) {
        Plan plan = getPlan(workspaceId, planId);
        LimitPolicy policy = policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("LimitPolicy", policyId));

        if (!planId.equals(policy.getPlanId())) {
            throw new ResourceNotFoundException("LimitPolicy with ID " + policyId + " does not belong to Plan " + planId);
        }

        policy.setEnabled(enabled);
        LimitPolicy saved = policyRepository.saveAndFlush(policy);

        plan.markUpdated();
        plan = planRepository.saveAndFlush(plan);

        List<LimitPolicy> allPolicies = policyRepository.findByWorkspaceIdAndPlanIdOrderByCreatedAtAsc(workspaceId, planId);
        emitPlanChangedEvent(plan, allPolicies, userId, "TOGGLE_LIMIT_POLICY", "Updated policy status on plan '" + plan.getName() + "'");

        return saved;
    }

    @Transactional
    public Plan activatePlan(UUID workspaceId, UUID userId, UUID planId) {
        Plan plan = getPlan(workspaceId, planId);
        plan.setStatus(ResourceStatus.ACTIVE);
        plan = planRepository.saveAndFlush(plan);

        List<LimitPolicy> allPolicies = policyRepository.findByWorkspaceIdAndPlanIdOrderByCreatedAtAsc(workspaceId, planId);
        emitPlanChangedEvent(plan, allPolicies, userId, "ACTIVATE_PLAN", "Activated plan '" + plan.getName() + "'");

        return plan;
    }

    @Transactional
    public Plan disablePlan(UUID workspaceId, UUID userId, UUID planId) {
        Plan plan = getPlan(workspaceId, planId);
        plan.setStatus(ResourceStatus.DISABLED);
        plan = planRepository.saveAndFlush(plan);

        List<LimitPolicy> allPolicies = policyRepository.findByWorkspaceIdAndPlanIdOrderByCreatedAtAsc(workspaceId, planId);
        emitPlanChangedEvent(plan, allPolicies, userId, "DISABLE_PLAN", "Disabled plan '" + plan.getName() + "'");

        return plan;
    }

    private void validatePolicy(LimitPolicy policy) {
        if (policy.getKind() == LimitPolicyKind.RATE) {
            if (policy.getCapacity() == null || policy.getCapacity() <= 0) {
                throw new InvalidInputException("RATE policy capacity must be greater than 0");
            }
            if (policy.getRefillTokens() == null || policy.getRefillTokens() <= 0) {
                throw new InvalidInputException("RATE policy refill tokens must be greater than 0");
            }
            if (policy.getRefillPeriodSeconds() == null || policy.getRefillPeriodSeconds() <= 0) {
                throw new InvalidInputException("RATE policy refill period seconds must be greater than 0");
            }
        } else if (policy.getKind() == LimitPolicyKind.QUOTA) {
            if (policy.getQuotaLimit() == null || policy.getQuotaLimit() <= 0) {
                throw new InvalidInputException("QUOTA policy limit must be greater than 0");
            }
            if (policy.getQuotaPeriod() == null) {
                throw new InvalidInputException("QUOTA policy period (DAY or MONTH) is required");
            }
        }
    }

    private void emitPlanChangedEvent(Plan plan, List<LimitPolicy> policies, UUID userId, String action, String summary) {
        List<LimitPolicyDto> policyDtos = policies.stream()
                .map(p -> new LimitPolicyDto(
                        p.getId(),
                        p.getRouteId(),
                        p.getKind(),
                        p.getCapacity(),
                        p.getRefillTokens(),
                        p.getRefillPeriodSeconds(),
                        p.getQuotaLimit(),
                        p.getQuotaPeriod(),
                        p.isEnabled()
                ))
                .toList();

        PlanConfigurationChangedV1 payload = new PlanConfigurationChangedV1(
                plan.getId(),
                plan.getWorkspaceId(),
                plan.getProductId(),
                plan.getName(),
                plan.getSlug(),
                plan.getStatus(),
                policyDtos,
                plan.getVersion(),
                plan.getUpdatedAt()
        );

        ConfigEventEnvelope<PlanConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                plan.getWorkspaceId(),
                "Plan",
                plan.getId(),
                plan.getVersion(),
                "PlanConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                plan.getWorkspaceId(),
                userId,
                action,
                "Plan",
                plan.getId(),
                null,
                summary,
                Map.of("name", plan.getName(), "slug", plan.getSlug()),
                outboxEvent
        );
    }
}
