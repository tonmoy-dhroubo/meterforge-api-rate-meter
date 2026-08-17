package io.meterforge.contracts.event;

import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;

import java.util.UUID;

public record LimitPolicyDto(
        UUID id,
        UUID routeId,
        LimitPolicyKind kind,
        Integer capacity,
        Integer refillTokens,
        Integer refillPeriodSeconds,
        Long quotaLimit,
        QuotaPeriod quotaPeriod,
        boolean enabled
) {}
