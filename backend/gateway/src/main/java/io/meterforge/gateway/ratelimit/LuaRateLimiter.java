package io.meterforge.gateway.ratelimit;

import io.meterforge.contracts.common.LimitPolicyKind;
import io.meterforge.contracts.common.QuotaPeriod;
import io.meterforge.contracts.projection.PolicyProjection;
import io.meterforge.contracts.projection.SubscriptionProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class LuaRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LuaRateLimiter.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> rateLimiterScript;

    public LuaRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            @SuppressWarnings("rawtypes") RedisScript<List> rateLimiterScript) {
        this.redisTemplate = redisTemplate;
        this.rateLimiterScript = rateLimiterScript;
    }

    public Mono<RateLimitDecision> evaluate(
            SubscriptionProjection subscription,
            UUID routeId,
            int costUnits) {

        List<PolicyProjection> applicablePolicies = filterApplicablePolicies(subscription, routeId);

        if (applicablePolicies.isEmpty()) {
            return Mono.just(RateLimitDecision.allow(999999, 0));
        }

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        args.add(String.valueOf(costUnits));
        args.add(String.valueOf(applicablePolicies.size()));

        for (int i = 0; i < applicablePolicies.size(); i++) {
            PolicyProjection policy = applicablePolicies.get(i);
            int keyIndex = i + 1;

            if (policy.kind() == LimitPolicyKind.RATE) {
                String key = "rf:v1:rate:{" + subscription.subscriptionId() + "}:" + policy.policyId();
                keys.add(key);

                args.add("RATE");
                args.add(policy.policyId().toString());
                args.add(String.valueOf(policy.capacity() != null ? policy.capacity() : 10));
                args.add(String.valueOf(policy.refillTokens() != null ? policy.refillTokens() : 10));
                args.add(String.valueOf(policy.refillPeriodSeconds() != null ? policy.refillPeriodSeconds() : 1));
                args.add(String.valueOf(policy.refillPeriodSeconds() != null ? policy.refillPeriodSeconds() * 3 : 60));
                args.add(String.valueOf(keyIndex));

            } else if (policy.kind() == LimitPolicyKind.QUOTA) {
                String windowId;
                long windowTtlSec;

                if (policy.quotaPeriod() == QuotaPeriod.MONTH) {
                    YearMonth ym = YearMonth.now(ZoneOffset.UTC);
                    windowId = ym.toString();
                    LocalDateTime endOfMonth = ym.atEndOfMonth().atTime(LocalTime.MAX);
                    windowTtlSec = Duration.between(LocalDateTime.now(ZoneOffset.UTC), endOfMonth).getSeconds() + 86400;
                } else {
                    LocalDate today = LocalDate.now(ZoneOffset.UTC);
                    windowId = today.toString();
                    LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
                    windowTtlSec = Duration.between(LocalDateTime.now(ZoneOffset.UTC), endOfDay).getSeconds() + 86400;
                }

                String key = "rf:v1:quota:{" + subscription.subscriptionId() + "}:" + policy.policyId() + ":" + windowId;
                keys.add(key);

                args.add("QUOTA");
                args.add(policy.policyId().toString());
                args.add(String.valueOf(policy.quotaLimit() != null ? policy.quotaLimit() : 1000));
                args.add("0");
                args.add("0");
                args.add(String.valueOf(Math.max(60, windowTtlSec)));
                args.add(String.valueOf(keyIndex));
            }
        }

        return redisTemplate.execute(rateLimiterScript, keys, args)
                .collectList()
                .map(results -> {
                    if (results.isEmpty() || !(results.get(0) instanceof List<?> list)) {
                        log.warn("Empty or invalid response from rate limiter Lua script");
                        return RateLimitDecision.deny(0, 5, 5, null);
                    }

                    long allowedVal = Long.parseLong(list.get(0).toString());
                    long remaining = Long.parseLong(list.get(1).toString());
                    long retryAfter = Long.parseLong(list.get(2).toString());
                    long resetAfter = Long.parseLong(list.get(3).toString());
                    String limitingPolicyStr = list.get(4).toString();
                    UUID limitingPolicyId = !limitingPolicyStr.isBlank() ? UUID.fromString(limitingPolicyStr) : null;

                    boolean allowed = (allowedVal == 1);
                    return new RateLimitDecision(allowed, remaining, retryAfter, resetAfter, limitingPolicyId);
                })
                .onErrorResume(e -> {
                    log.error("Redis rate limiter execution failure: {}", e.getMessage(), e);
                    // Ambiguous / unavailable Redis decision -> return denial with fail-safe error
                    return Mono.just(RateLimitDecision.deny(0, 10, 10, null));
                });
    }

    private List<PolicyProjection> filterApplicablePolicies(SubscriptionProjection subscription, UUID routeId) {
        if (subscription.policies() == null) {
            return List.of();
        }

        return subscription.policies().stream()
                .filter(PolicyProjection::enabled)
                .filter(p -> p.routeId() == null || (routeId != null && p.routeId().equals(routeId)))
                .toList();
    }
}
