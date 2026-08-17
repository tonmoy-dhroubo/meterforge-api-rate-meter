package io.meterforge.controlplane.plan.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LimitPolicyRepository extends JpaRepository<LimitPolicy, UUID> {

    List<LimitPolicy> findByWorkspaceIdAndPlanIdOrderByCreatedAtAsc(UUID workspaceId, UUID planId);

    Optional<LimitPolicy> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
