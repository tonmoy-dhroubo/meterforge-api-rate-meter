package io.meterforge.controlplane.usage.api.dto;

import java.time.Instant;

public record TimeseriesBucketDto(
        Instant bucketStart,
        long totalRequests,
        long allowedRequests,
        long rateLimitedRequests,
        long errorRequests,
        long totalUnits,
        double avgLatencyMs
) {}
