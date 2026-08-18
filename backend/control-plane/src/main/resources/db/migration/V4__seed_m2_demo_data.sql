-- Seed demo consumer 'Northstar Labs'
INSERT INTO meterforge.consumers (id, workspace_id, name, external_reference, status, created_at, updated_at, version)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Northstar Labs', 'EXT-NORTHSTAR-01', 'ACTIVE', NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Seed demo application 'Northstar Demo App'
INSERT INTO meterforge.consumer_applications (id, workspace_id, consumer_id, name, status, created_at, updated_at, version)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dddddddd-dddd-dddd-dddd-dddddddddddd', 'Northstar Demo App', 'ACTIVE', NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Seed demo plan 'Free Tier' under Weather API
INSERT INTO meterforge.plans (id, workspace_id, product_id, name, slug, status, created_at, updated_at, version)
VALUES
    ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Free Tier', 'free-tier', 'ACTIVE', NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Seed limit policies for Free Tier:
-- 1) Token Bucket Rate Policy: capacity = 5, refill = 5 per 10s
-- 2) Quota Policy: 100 units / day
INSERT INTO meterforge.limit_policies (id, workspace_id, plan_id, route_id, kind, capacity, refill_tokens, refill_period_seconds, quota_limit, quota_period, enabled, created_at, updated_at, version)
VALUES
    ('11112222-3333-4444-5555-666677778888', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ffffffff-ffff-ffff-ffff-ffffffffffff', null, 'RATE', 5, 5, 10, null, null, true, NOW(), NOW(), 1),
    ('22223333-4444-5555-6666-777788889999', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ffffffff-ffff-ffff-ffff-ffffffffffff', null, 'QUOTA', null, null, null, 100, 'DAY', true, NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Seed demo credential for Northstar Demo App (Public ID: nsdemo123456)
INSERT INTO meterforge.api_credentials (id, workspace_id, application_id, public_id, secret_hmac, display_prefix, display_last_four, environment, status, expires_at, revoked_at, created_at, updated_at, version)
VALUES
    ('99999999-9999-9999-9999-999999999999', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'nsdemo123456', '53a6a7444edad27f3b2165d6e2b7275ea954dc86b6d41884b3df2adab0bad695', 'mf_dev_nsdem', '9999', 'dev', 'ACTIVE', null, null, NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Seed demo subscription linking Northstar Demo App to Weather API Free Tier
INSERT INTO meterforge.subscriptions (id, workspace_id, application_id, product_id, plan_id, status, effective_from, effective_to, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888888', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'ffffffff-ffff-ffff-ffff-ffffffffffff', 'ACTIVE', NOW(), null, NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;
