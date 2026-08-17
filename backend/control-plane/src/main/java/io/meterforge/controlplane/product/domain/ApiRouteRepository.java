package io.meterforge.controlplane.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiRouteRepository extends JpaRepository<ApiRoute, UUID> {
    List<ApiRoute> findByWorkspaceIdAndProductIdOrderByPriorityDescCreatedAtAsc(UUID workspaceId, UUID productId);
    List<ApiRoute> findByProductId(UUID productId);
    Optional<ApiRoute> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    Optional<ApiRoute> findByIdAndWorkspaceIdAndProductId(UUID id, UUID workspaceId, UUID productId);
    boolean existsByProductIdAndHttpMethodAndPathPattern(UUID productId, String httpMethod, String pathPattern);
}
