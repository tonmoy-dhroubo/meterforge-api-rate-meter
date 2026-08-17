-- MeterForge Milestone M4 - Usage Events and Aggregations Schema

-- Raw usage telemetry events (immutable stream from gateway)
CREATE TABLE IF NOT EXISTS meterforge.usage_events (
    event_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    workspace_id UUID,
    product_id UUID,
    route_id UUID,
    consumer_id UUID,
    application_id UUID,
    credential_id UUID,
    subscription_id UUID,
    request_id VARCHAR(255) NOT NULL,
    http_method VARCHAR(20) NOT NULL,
    route_template VARCHAR(1024),
    decision VARCHAR(50) NOT NULL,
    outcome VARCHAR(50) NOT NULL,
    status_code INT NOT NULL,
    usage_units INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    limiting_policy_id UUID,
    gateway_instance_id VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_usage_events_workspace_occurred ON meterforge.usage_events(workspace_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_usage_events_request_id ON meterforge.usage_events(request_id);
CREATE INDEX IF NOT EXISTS idx_usage_events_product ON meterforge.usage_events(workspace_id, product_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_usage_events_consumer ON meterforge.usage_events(workspace_id, consumer_id, occurred_at DESC);

-- Hourly aggregated table (pre-calculated rollups)
CREATE TABLE IF NOT EXISTS meterforge.usage_hourly (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_start TIMESTAMPTZ NOT NULL,
    workspace_id UUID,
    product_id UUID,
    route_id UUID,
    consumer_id UUID,
    application_id UUID,
    subscription_id UUID,
    decision VARCHAR(50) NOT NULL,
    status_class VARCHAR(10) NOT NULL,
    total_requests BIGINT NOT NULL DEFAULT 0,
    total_units BIGINT NOT NULL DEFAULT 0,
    total_latency_ms BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_usage_hourly UNIQUE NULLS NOT DISTINCT (
        bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class
    )
);

CREATE INDEX IF NOT EXISTS idx_usage_hourly_workspace_bucket ON meterforge.usage_hourly(workspace_id, bucket_start DESC);

-- Daily aggregated table (pre-calculated daily rollups)
CREATE TABLE IF NOT EXISTS meterforge.usage_daily (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_start TIMESTAMPTZ NOT NULL,
    workspace_id UUID,
    product_id UUID,
    route_id UUID,
    consumer_id UUID,
    application_id UUID,
    subscription_id UUID,
    decision VARCHAR(50) NOT NULL,
    status_class VARCHAR(10) NOT NULL,
    total_requests BIGINT NOT NULL DEFAULT 0,
    total_units BIGINT NOT NULL DEFAULT 0,
    total_latency_ms BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_usage_daily UNIQUE NULLS NOT DISTINCT (
        bucket_start, workspace_id, product_id, route_id, consumer_id, application_id, subscription_id, decision, status_class
    )
);

CREATE INDEX IF NOT EXISTS idx_usage_daily_workspace_bucket ON meterforge.usage_daily(workspace_id, bucket_start DESC);
