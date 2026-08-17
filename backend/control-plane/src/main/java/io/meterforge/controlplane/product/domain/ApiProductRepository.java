package io.meterforge.controlplane.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiProductRepository extends JpaRepository<ApiProduct, UUID> {
    List<ApiProduct> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    Optional<ApiProduct> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    Optional<ApiProduct> findByWorkspaceIdAndSlug(UUID workspaceId, String slug);
    boolean existsByWorkspaceIdAndSlug(UUID workspaceId, String slug);
    boolean existsByWorkspaceIdAndGatewayBasePath(UUID workspaceId, String gatewayBasePath);
}
