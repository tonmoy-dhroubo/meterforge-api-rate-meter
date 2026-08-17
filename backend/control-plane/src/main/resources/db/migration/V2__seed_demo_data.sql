-- Seed demo users (password: 'password123' hashed with BCrypt)
INSERT INTO meterforge.users (id, email, password_hash, status, created_at, updated_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'owner@meterforge.local', '$2a$10$7xTfAGEKRrjTmyhMbjtq4ebBKJuFV/UqOoJaJHYD3GRPutQxtaVmO', 'ACTIVE', NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222222', 'member@meterforge.local', '$2a$10$7xTfAGEKRrjTmyhMbjtq4ebBKJuFV/UqOoJaJHYD3GRPutQxtaVmO', 'ACTIVE', NOW(), NOW()),
    ('33333333-3333-3333-3333-333333333333', 'viewer@meterforge.local', '$2a$10$7xTfAGEKRrjTmyhMbjtq4ebBKJuFV/UqOoJaJHYD3GRPutQxtaVmO', 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Seed demo workspace 'Acme APIs'
INSERT INTO meterforge.workspaces (id, name, slug, status, created_at, updated_at, version)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Acme APIs', 'acme-apis', 'ACTIVE', NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Seed workspace memberships
INSERT INTO meterforge.workspace_members (workspace_id, user_id, role, status, created_at, updated_at)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'OWNER', 'ACTIVE', NOW(), NOW()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'MEMBER', 'ACTIVE', NOW(), NOW()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 'VIEWER', 'ACTIVE', NOW(), NOW())
ON CONFLICT (workspace_id, user_id) DO NOTHING;

-- Seed demo product 'Weather API'
INSERT INTO meterforge.api_products (id, workspace_id, name, slug, upstream_base_url, gateway_base_path, status, created_at, updated_at, version)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Weather API', 'weather-api', 'http://wiremock:8080', '/v1/forecast', 'ACTIVE', NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Seed demo route 'GET /v1/forecast/{city}'
INSERT INTO meterforge.api_routes (id, workspace_id, product_id, http_method, path_pattern, upstream_path, cost_units, priority, status, created_at, updated_at, version)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'GET', '/v1/forecast/{city}', null, 1, 10, 'ACTIVE', NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;
