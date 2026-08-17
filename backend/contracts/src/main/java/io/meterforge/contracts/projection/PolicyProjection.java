package io.meterforge.contracts.projection;

import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;

import java.util.UUID;

public record PolicyProjection(
        UUID policyId,
        UUID routeId,
        LimitPolicyKind kind,
        Integer capacity,
        Integer refillTokens,
        Integer refillPeriodSeconds,
        Long quotaLimit,
        QuotaPeriod quotaPeriod,
        boolean enabled
) {}
