package io.meterforge.controlplane.credential.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {

    List<ApiCredential> findByWorkspaceIdAndApplicationIdOrderByCreatedAtDesc(UUID workspaceId, UUID applicationId);

    Optional<ApiCredential> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<ApiCredential> findByPublicId(String publicId);
}
