package io.meterforge.controlplane.subscription.application;

import io.meterforge.contracts.common.ResourceStatus;
import io.meterforge.contracts.event.ConfigEventEnvelope;
import io.meterforge.contracts.event.SubscriptionConfigurationChangedV1;
import io.meterforge.controlplane.common.application.TransactionalMutationService;
import io.meterforge.controlplane.common.exception.ResourceConflictException;
import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.consumer.domain.ConsumerApplication;
import io.meterforge.controlplane.consumer.domain.ConsumerApplicationRepository;
import io.meterforge.controlplane.plan.domain.Plan;
import io.meterforge.controlplane.plan.domain.PlanRepository;
import io.meterforge.controlplane.product.domain.ApiProduct;
import io.meterforge.controlplane.product.domain.ApiProductRepository;
import io.meterforge.controlplane.subscription.domain.Subscription;
import io.meterforge.controlplane.subscription.domain.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ConsumerApplicationRepository applicationRepository;
    private final ApiProductRepository productRepository;
    private final PlanRepository planRepository;
    private final TransactionalMutationService mutationService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               ConsumerApplicationRepository applicationRepository,
                               ApiProductRepository productRepository,
                               PlanRepository planRepository,
                               TransactionalMutationService mutationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.planRepository = planRepository;
        this.mutationService = mutationService;
    }

    @Transactional(readOnly = true)
    public List<Subscription> listSubscriptions(UUID workspaceId, UUID applicationId) {
        if (applicationId != null) {
            return subscriptionRepository.findByWorkspaceIdAndApplicationId(workspaceId, applicationId);
        }
        return subscriptionRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Subscription getSubscription(UUID workspaceId, UUID subscriptionId) {
        return subscriptionRepository.findByIdAndWorkspaceId(subscriptionId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));
    }

    @Transactional
    public Subscription createSubscription(UUID workspaceId, UUID userId, UUID applicationId, UUID productId, UUID planId) {
        ConsumerApplication app = applicationRepository.findByIdAndWorkspaceId(applicationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("ConsumerApplication", applicationId));

        ApiProduct product = productRepository.findByIdAndWorkspaceId(productId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiProduct", productId));

        Plan plan = planRepository.findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));

        if (!plan.getProductId().equals(product.getId())) {
            throw new ResourceConflictException("Plan '" + plan.getName() + "' does not belong to product '" + product.getName() + "'.");
        }

        if (subscriptionRepository.existsByApplicationIdAndProductIdAndStatus(applicationId, productId, ResourceStatus.ACTIVE)) {
            throw new ResourceConflictException("Application '" + app.getName() + "' already has an active subscription to product '" + product.getName() + "'.");
        }

        Subscription subscription = new Subscription(null, workspaceId, applicationId, productId, planId);
        subscription = subscriptionRepository.save(subscription);

        SubscriptionConfigurationChangedV1 payload = new SubscriptionConfigurationChangedV1(
                subscription.getId(),
                subscription.getWorkspaceId(),
                subscription.getApplicationId(),
                subscription.getProductId(),
                subscription.getPlanId(),
                subscription.getStatus(),
                subscription.getEffectiveFrom(),
                subscription.getEffectiveTo(),
                subscription.getVersion(),
                subscription.getUpdatedAt()
        );

        ConfigEventEnvelope<SubscriptionConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "Subscription",
                subscription.getId(),
                subscription.getVersion(),
                "SubscriptionConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "CREATE_SUBSCRIPTION",
                "Subscription",
                subscription.getId(),
                null,
                "Subscribed application '" + app.getName() + "' to product '" + product.getName() + "' with plan '" + plan.getName() + "'",
                Map.of("applicationId", applicationId, "productId", productId, "planId", planId),
                outboxEvent
        );

        return subscription;
    }

    @Transactional
    public Subscription cancelSubscription(UUID workspaceId, UUID userId, UUID subscriptionId) {
        Subscription subscription = getSubscription(workspaceId, subscriptionId);
        subscription.cancel();
        subscription = subscriptionRepository.save(subscription);

        SubscriptionConfigurationChangedV1 payload = new SubscriptionConfigurationChangedV1(
                subscription.getId(),
                subscription.getWorkspaceId(),
                subscription.getApplicationId(),
                subscription.getProductId(),
                subscription.getPlanId(),
                subscription.getStatus(),
                subscription.getEffectiveFrom(),
                subscription.getEffectiveTo(),
                subscription.getVersion(),
                subscription.getUpdatedAt()
        );

        ConfigEventEnvelope<SubscriptionConfigurationChangedV1> outboxEvent = ConfigEventEnvelope.of(
                workspaceId,
                "Subscription",
                subscription.getId(),
                subscription.getVersion(),
                "SubscriptionConfigurationChangedV1",
                payload
        );

        mutationService.recordMutation(
                workspaceId,
                userId,
                "CANCEL_SUBSCRIPTION",
                "Subscription",
                subscription.getId(),
                null,
                "Cancelled subscription " + subscription.getId(),
                Map.of("subscriptionId", subscription.getId()),
                outboxEvent
        );

        return subscription;
    }
}
