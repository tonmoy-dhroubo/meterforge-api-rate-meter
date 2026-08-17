package io.meterforge.controlplane.subscription.domain;

import io.meterforge.contracts.common.ResourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<Subscription> findByWorkspaceIdAndApplicationId(UUID workspaceId, UUID applicationId);

    Optional<Subscription> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Subscription> findByApplicationIdAndProductIdAndStatus(UUID applicationId, UUID productId, ResourceStatus status);

    boolean existsByApplicationIdAndProductIdAndStatus(UUID applicationId, UUID productId, ResourceStatus status);
}
