package io.meterforge.controlplane.usage.api.dto;

public record UsageSummaryResponse(
        long totalRequests,
        long allowedRequests,
        long rateLimitedRequests,
        long blockedRequests,
        long clientErrorRequests,
        long serverErrorRequests,
        long totalUnitsConsumed,
        double avgLatencyMs
) {}
