CREATE SCHEMA IF NOT EXISTS meterforge;

-- Users table (staff accounts)
CREATE TABLE IF NOT EXISTS meterforge.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON meterforge.users(email);

-- Workspaces table (tenant boundary)
CREATE TABLE IF NOT EXISTS meterforge.workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workspaces_slug UNIQUE (slug)
);

CREATE INDEX IF NOT EXISTS idx_workspaces_slug ON meterforge.workspaces(slug);

-- Workspace members table (RBAC)
CREATE TABLE IF NOT EXISTS meterforge.workspace_members (
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES meterforge.users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_workspace_members_user ON meterforge.workspace_members(user_id);

-- API Products table
CREATE TABLE IF NOT EXISTS meterforge.api_products (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    upstream_base_url VARCHAR(1024) NOT NULL,
    gateway_base_path VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_api_products_workspace_slug UNIQUE (workspace_id, slug),
    CONSTRAINT uq_api_products_workspace_base_path UNIQUE (workspace_id, gateway_base_path)
);

CREATE INDEX IF NOT EXISTS idx_api_products_workspace ON meterforge.api_products(workspace_id);

-- API Routes table
CREATE TABLE IF NOT EXISTS meterforge.api_routes (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES meterforge.api_products(id) ON DELETE CASCADE,
    http_method VARCHAR(20) NOT NULL,
    path_pattern VARCHAR(1024) NOT NULL,
    upstream_path VARCHAR(1024),
    cost_units INT NOT NULL DEFAULT 1,
    priority INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_api_routes_product_method_path UNIQUE (product_id, http_method, path_pattern),
    CONSTRAINT chk_cost_units_positive CHECK (cost_units >= 1),
    CONSTRAINT chk_priority_non_negative CHECK (priority >= 0)
);

CREATE INDEX IF NOT EXISTS idx_api_routes_workspace_product ON meterforge.api_routes(workspace_id, product_id);

-- Audit logs table (append-only)
CREATE TABLE IF NOT EXISTS meterforge.audit_logs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID,
    request_id VARCHAR(255),
    summary TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_workspace_created ON meterforge.audit_logs(workspace_id, created_at DESC);

-- Outbox events table (transactional outbox)
CREATE TABLE IF NOT EXISTS meterforge.outbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    workspace_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_unpublished ON meterforge.outbox_events(published_at, occurred_at);
