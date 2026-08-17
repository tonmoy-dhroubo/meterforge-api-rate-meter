package io.meterforge.controlplane.plan.api.dto;

import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateLimitPolicyRequest(
        UUID routeId,

        @NotNull(message = "Policy kind (RATE or QUOTA) is required")
        LimitPolicyKind kind,

        Integer capacity,
        Integer refillTokens,
        Integer refillPeriodSeconds,

        Long quotaLimit,
        QuotaPeriod quotaPeriod
) {}
