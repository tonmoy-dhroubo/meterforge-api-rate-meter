package io.meterforge.controlplane.plan.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByWorkspaceIdAndProductIdOrderByCreatedAtDesc(UUID workspaceId, UUID productId);

    List<Plan> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<Plan> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByProductIdAndSlug(UUID productId, String slug);

    boolean existsByWorkspaceIdAndProductIdAndSlug(UUID workspaceId, UUID productId, String slug);
}
