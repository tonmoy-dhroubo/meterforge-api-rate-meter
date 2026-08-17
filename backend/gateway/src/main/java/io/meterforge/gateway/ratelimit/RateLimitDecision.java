package io.meterforge.gateway.ratelimit;

import java.util.UUID;

public record RateLimitDecision(
        boolean allowed,
        long remaining,
        long retryAfterSeconds,
        long resetAfterSeconds,
        UUID limitingPolicyId
) {
    public static RateLimitDecision allow(long remaining, long resetAfterSeconds) {
        return new RateLimitDecision(true, remaining, 0, resetAfterSeconds, null);
    }

    public static RateLimitDecision deny(long remaining, long retryAfterSeconds, long resetAfterSeconds, UUID limitingPolicyId) {
        return new RateLimitDecision(false, remaining, retryAfterSeconds, resetAfterSeconds, limitingPolicyId);
    }
}
