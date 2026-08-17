package io.meterforge.controlplane.usage.api.dto;

import java.util.List;

public record RawUsageEventsPageDto(
        List<RawUsageEventDto> items,
        long total,
        int limit,
        int offset
) {}
