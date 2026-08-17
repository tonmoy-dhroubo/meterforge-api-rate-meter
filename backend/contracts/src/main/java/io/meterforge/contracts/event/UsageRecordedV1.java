package io.meterforge.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record UsageRecordedV1(
        int schemaVersion,
        UUID eventId,
        Instant occurredAt,
        String requestId,
        UUID workspaceId,
        UUID productId,
        UUID routeId,
        UUID consumerId,
        UUID consumerApplicationId,
        UUID credentialId,
        UUID subscriptionId,
        String method,
        String routeTemplate,
        UsageDecision decision,
        UsageOutcome outcome,
        int statusCode,
        int usageUnits,
        long latencyMs,
        UUID limitingPolicyId,
        String gatewayInstanceId
) {
    public static UsageRecordedV1 create(
            String requestId,
            UUID workspaceId,
            UUID productId,
            UUID routeId,
            UUID consumerId,
            UUID consumerApplicationId,
            UUID credentialId,
            UUID subscriptionId,
            String method,
            String routeTemplate,
            UsageDecision decision,
            UsageOutcome outcome,
            int statusCode,
            int usageUnits,
            long latencyMs,
            UUID limitingPolicyId,
            String gatewayInstanceId
    ) {
        return new UsageRecordedV1(
                1,
                UUID.randomUUID(),
                Instant.now(),
                requestId,
                workspaceId,
                productId,
                routeId,
                consumerId,
                consumerApplicationId,
                credentialId,
                subscriptionId,
                method,
                routeTemplate,
                decision,
                outcome,
                statusCode,
                usageUnits,
                latencyMs,
                limitingPolicyId,
                gatewayInstanceId != null ? gatewayInstanceId : "gateway-1"
        );
    }
}
