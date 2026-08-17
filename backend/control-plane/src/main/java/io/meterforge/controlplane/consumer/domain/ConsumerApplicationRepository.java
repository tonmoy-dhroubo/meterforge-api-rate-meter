package io.meterforge.controlplane.consumer.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerApplicationRepository extends JpaRepository<ConsumerApplication, UUID> {

    List<ConsumerApplication> findByWorkspaceIdAndConsumerIdOrderByCreatedAtDesc(UUID workspaceId, UUID consumerId);

    List<ConsumerApplication> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<ConsumerApplication> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByConsumerIdAndName(UUID consumerId, String name);

    long countByWorkspaceIdAndConsumerId(UUID workspaceId, UUID consumerId);
}
