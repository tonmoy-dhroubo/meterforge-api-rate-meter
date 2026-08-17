package io.meterforge.controlplane.usage.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RawUsageEventDto(
        UUID eventId,
        Instant occurredAt,
        String requestId,
        UUID workspaceId,
        UUID productId,
        UUID routeId,
        String httpMethod,
        String routeTemplate,
        UUID consumerId,
        UUID applicationId,
        UUID credentialId,
        UUID subscriptionId,
        String decision,
        String outcome,
        int statusCode,
        int usageUnits,
        long latencyMs,
        UUID limitingPolicyId,
        String gatewayInstanceId
) {}
