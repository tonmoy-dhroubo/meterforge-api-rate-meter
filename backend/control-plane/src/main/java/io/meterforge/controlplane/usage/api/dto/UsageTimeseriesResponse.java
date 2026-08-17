package io.meterforge.controlplane.usage.api.dto;

import java.time.Instant;
import java.util.List;

public record UsageTimeseriesResponse(
        String granularity,
        Instant from,
        Instant to,
        List<TimeseriesBucketDto> buckets
) {}
