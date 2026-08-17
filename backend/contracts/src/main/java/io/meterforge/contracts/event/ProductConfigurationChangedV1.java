package io.meterforge.contracts.event;

import io.meterforge.contracts.common.ResourceStatus;
import java.util.UUID;

public record ProductConfigurationChangedV1(
        UUID productId,
        UUID workspaceId,
        String name,
        String slug,
        String upstreamBaseUrl,
        String gatewayBasePath,
        ResourceStatus status,
        long version
) {}
