-- MeterForge Atomic Multi-Policy Rate and Quota Limiter
-- Evaluates Token Bucket Rate policies and Fixed Window Quota policies atomically.
-- Invariant: If any policy denies, no counters are mutated.

local now = redis.call('TIME')
local now_sec = tonumber(now[1])
local now_ms = now_sec * 1000 + math.floor(tonumber(now[2]) / 1000)

local cost = tonumber(ARGV[1])
local num_policies = tonumber(ARGV[2])

if num_policies == 0 then
    return {1, 999999, 0, 0, ""}
end

local denied = 0
local limiting_policy_id = ""
local min_remaining = 999999999
local retry_after_sec = 0
local reset_after_sec = 0

local new_tokens = {}
local new_ts = {}
local new_quota = {}

-- PHASE 1: EVALUATION (READ-ONLY)
for i = 1, num_policies do
    local offset = 2 + (i - 1) * 7
    local kind = ARGV[offset + 1]
    local policy_id = ARGV[offset + 2]
    local capacity_or_limit = tonumber(ARGV[offset + 3])
    local refill_tokens = tonumber(ARGV[offset + 4])
    local refill_period_sec = tonumber(ARGV[offset + 5])
    local window_ttl_sec = tonumber(ARGV[offset + 6])
    local key_idx = tonumber(ARGV[offset + 7])
    local key = KEYS[key_idx]

    if kind == "RATE" then
        local res = redis.call('HMGET', key, 'tokens', 'ts')
        local stored_tokens = res[1]
        local stored_ts = res[2]

        local curr_tokens = capacity_or_limit
        local last_ts = now_ms

        if stored_tokens and stored_ts then
            curr_tokens = tonumber(stored_tokens)
            last_ts = tonumber(stored_ts)
            local elapsed_ms = now_ms - last_ts
            if elapsed_ms > 0 and refill_period_sec > 0 then
                local refill = math.floor((elapsed_ms * refill_tokens) / (refill_period_sec * 1000))
                if refill > 0 then
                    curr_tokens = math.min(capacity_or_limit, curr_tokens + refill)
                    -- advance last_ts by the discrete refill interval consumed
                    local consumed_time_ms = math.floor((refill * refill_period_sec * 1000) / refill_tokens)
                    last_ts = last_ts + consumed_time_ms
                end
            end
        end

        if curr_tokens < cost then
            denied = 1
            if limiting_policy_id == "" then
                limiting_policy_id = policy_id
            end
            local needed = cost - curr_tokens
            local wait_sec = math.ceil((needed * refill_period_sec) / refill_tokens)
            if wait_sec < 1 then wait_sec = 1 end
            if wait_sec > retry_after_sec then
                retry_after_sec = wait_sec
            end
            min_remaining = 0
        else
            local remaining = curr_tokens - cost
            if remaining < min_remaining then
                min_remaining = remaining
            end
            new_tokens[i] = remaining
            new_ts[i] = last_ts
        end

    elseif kind == "QUOTA" then
        local stored = redis.call('GET', key)
        local curr_used = 0
        if stored then
            curr_used = tonumber(stored)
        end

        if (curr_used + cost) > capacity_or_limit then
            denied = 1
            if limiting_policy_id == "" then
                limiting_policy_id = policy_id
            end
            local key_ttl = redis.call('TTL', key)
            if key_ttl > 0 and key_ttl > reset_after_sec then
                reset_after_sec = key_ttl
            elseif window_ttl_sec > reset_after_sec then
                reset_after_sec = window_ttl_sec
            end
            min_remaining = 0
        else
            local remaining = capacity_or_limit - (curr_used + cost)
            if remaining < min_remaining then
                min_remaining = remaining
            end
            new_quota[i] = curr_used + cost
        end
    end
end

-- PHASE 2: MUTATION (ONLY IF ALL PASS)
if denied == 1 then
    if min_remaining == 999999999 then min_remaining = 0 end
    return {0, min_remaining, retry_after_sec, reset_after_sec, limiting_policy_id}
end

for i = 1, num_policies do
    local offset = 2 + (i - 1) * 7
    local kind = ARGV[offset + 1]
    local refill_period_sec = tonumber(ARGV[offset + 5])
    local window_ttl_sec = tonumber(ARGV[offset + 6])
    local key_idx = tonumber(ARGV[offset + 7])
    local key = KEYS[key_idx]

    if kind == "RATE" then
        redis.call('HMSET', key, 'tokens', new_tokens[i], 'ts', new_ts[i])
        local expire_sec = math.max(60, refill_period_sec * 3)
        redis.call('EXPIRE', key, expire_sec)
    elseif kind == "QUOTA" then
        redis.call('SET', key, new_quota[i])
        local current_ttl = redis.call('TTL', key)
        if current_ttl <= 0 and window_ttl_sec > 0 then
            redis.call('EXPIRE', key, window_ttl_sec)
        end
    end
end

if min_remaining == 999999999 then min_remaining = 0 end
return {1, min_remaining, 0, reset_after_sec, ""}
