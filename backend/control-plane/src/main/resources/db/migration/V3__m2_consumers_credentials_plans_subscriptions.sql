-- Consumers table
CREATE TABLE IF NOT EXISTS meterforge.consumers (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    external_reference VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_consumers_workspace_name UNIQUE (workspace_id, name)
);

CREATE INDEX IF NOT EXISTS idx_consumers_workspace ON meterforge.consumers(workspace_id);

-- Consumer Applications table
CREATE TABLE IF NOT EXISTS meterforge.consumer_applications (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    consumer_id UUID NOT NULL REFERENCES meterforge.consumers(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_consumer_apps_consumer_name UNIQUE (consumer_id, name)
);

CREATE INDEX IF NOT EXISTS idx_consumer_apps_workspace_consumer ON meterforge.consumer_applications(workspace_id, consumer_id);

-- API Credentials table (never stores raw key secrets)
CREATE TABLE IF NOT EXISTS meterforge.api_credentials (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES meterforge.consumer_applications(id) ON DELETE CASCADE,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    secret_hmac VARCHAR(255) NOT NULL,
    display_prefix VARCHAR(32) NOT NULL,
    display_last_four VARCHAR(16) NOT NULL,
    environment VARCHAR(32) NOT NULL DEFAULT 'dev',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_api_credentials_public_id ON meterforge.api_credentials(public_id);
CREATE INDEX IF NOT EXISTS idx_api_credentials_app ON meterforge.api_credentials(workspace_id, application_id);

-- Plans table
CREATE TABLE IF NOT EXISTS meterforge.plans (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES meterforge.api_products(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_plans_product_slug UNIQUE (product_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_plans_workspace_product ON meterforge.plans(workspace_id, product_id);

-- Limit Policies table (Token bucket RATE and Fixed Window QUOTA)
CREATE TABLE IF NOT EXISTS meterforge.limit_policies (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES meterforge.plans(id) ON DELETE CASCADE,
    route_id UUID REFERENCES meterforge.api_routes(id) ON DELETE CASCADE,
    kind VARCHAR(50) NOT NULL,
    capacity INT,
    refill_tokens INT,
    refill_period_seconds INT,
    quota_limit BIGINT,
    quota_period VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_limit_policy_kind CHECK (kind IN ('RATE', 'QUOTA')),
    CONSTRAINT chk_rate_fields CHECK (kind <> 'RATE' OR (capacity IS NOT NULL AND refill_tokens IS NOT NULL AND refill_period_seconds IS NOT NULL)),
    CONSTRAINT chk_quota_fields CHECK (kind <> 'QUOTA' OR (quota_limit IS NOT NULL AND quota_period IN ('DAY', 'MONTH')))
);

CREATE INDEX IF NOT EXISTS idx_limit_policies_plan ON meterforge.limit_policies(workspace_id, plan_id);

-- Subscriptions table (links Application to Product Plan)
CREATE TABLE IF NOT EXISTS meterforge.subscriptions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES meterforge.workspaces(id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES meterforge.consumer_applications(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES meterforge.api_products(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES meterforge.plans(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_lookup ON meterforge.subscriptions(workspace_id, application_id, product_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_app_product_sub ON meterforge.subscriptions (application_id, product_id) WHERE (status = 'ACTIVE');
