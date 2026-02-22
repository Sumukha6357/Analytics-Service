CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    rate_limit_per_min INT NOT NULL DEFAULT 6000,
    strict_schema BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tenant_api_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    key_hash TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE SEQUENCE events_raw_id_seq;

CREATE TABLE events_raw (
    id BIGINT NOT NULL DEFAULT nextval('events_raw_id_seq'),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    received_at TIMESTAMPTZ NOT NULL,
    event_ts TIMESTAMPTZ NOT NULL,
    event_name TEXT NOT NULL,
    user_id TEXT,
    anonymous_id TEXT,
    session_id TEXT,
    event_uuid UUID,
    schema_version INT NOT NULL DEFAULT 1,
    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key TEXT,
    ingestion_id UUID NOT NULL,
    PRIMARY KEY (id, received_at)
) PARTITION BY RANGE (received_at);

CREATE INDEX idx_events_raw_tenant_received ON events_raw (tenant_id, received_at DESC);
CREATE INDEX idx_events_raw_tenant_event_received ON events_raw (tenant_id, event_name, received_at DESC);
CREATE INDEX idx_events_raw_tenant_user_received ON events_raw (tenant_id, user_id, received_at DESC);
CREATE INDEX idx_events_raw_tenant_event_ts ON events_raw (tenant_id, event_ts DESC);
CREATE UNIQUE INDEX uq_events_raw_tenant_event_uuid
    ON events_raw (tenant_id, event_uuid)
    WHERE event_uuid IS NOT NULL;

CREATE TABLE ingestion_idempotency (
    tenant_id UUID NOT NULL,
    route TEXT NOT NULL DEFAULT '/v1/track',
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_code INT NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, route, idempotency_key)
);

CREATE TABLE rate_limit_counters (
    tenant_id UUID NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    counter INT NOT NULL,
    PRIMARY KEY (tenant_id, window_start)
);

CREATE TABLE analytics_query_rate_limit_counters (
    tenant_id UUID NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    counter INT NOT NULL,
    PRIMARY KEY (tenant_id, window_start)
);
CREATE INDEX idx_analytics_query_rate_limit_window
    ON analytics_query_rate_limit_counters (window_start);

CREATE TABLE job_queue (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID,
    job_type TEXT NOT NULL,
    job_key TEXT,
    not_before TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    locked_by TEXT,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 10,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_job_queue_status_not_before ON job_queue (status, not_before);
CREATE INDEX idx_job_queue_locked_at ON job_queue (locked_at);
CREATE INDEX idx_job_queue_tenant_status ON job_queue (tenant_id, status);
CREATE INDEX idx_job_queue_pending ON job_queue (status, not_before, locked_at);
CREATE UNIQUE INDEX uq_job_queue_active_key
    ON job_queue (tenant_id, job_type, job_key)
    WHERE status IN ('PENDING', 'RUNNING') AND job_key IS NOT NULL;

CREATE TABLE aggregates_event_counts_minute (
    tenant_id UUID NOT NULL,
    bucket_start TIMESTAMPTZ NOT NULL,
    event_name TEXT NOT NULL,
    count BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, bucket_start, event_name)
);
CREATE INDEX idx_agg_event_minute_tenant_bucket
    ON aggregates_event_counts_minute (tenant_id, bucket_start);

CREATE TABLE aggregates_event_counts_hour (
    tenant_id UUID NOT NULL,
    bucket_start TIMESTAMPTZ NOT NULL,
    event_name TEXT NOT NULL,
    count BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, bucket_start, event_name)
);
CREATE INDEX idx_agg_event_hour_tenant_bucket
    ON aggregates_event_counts_hour (tenant_id, bucket_start);

CREATE TABLE aggregates_event_counts_day (
    tenant_id UUID NOT NULL,
    bucket_start DATE NOT NULL,
    event_name TEXT NOT NULL,
    count BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, bucket_start, event_name)
);
CREATE INDEX idx_agg_event_day_tenant_bucket
    ON aggregates_event_counts_day (tenant_id, bucket_start);

CREATE TABLE aggregates_active_users_daily (
    tenant_id UUID NOT NULL,
    day DATE NOT NULL,
    dau BIGINT NOT NULL,
    wau BIGINT,
    mau BIGINT,
    PRIMARY KEY (tenant_id, day)
);

CREATE TABLE daily_active_users_set (
    tenant_id UUID NOT NULL,
    day DATE NOT NULL,
    user_key TEXT NOT NULL,
    PRIMARY KEY (tenant_id, day, user_key)
);
CREATE INDEX idx_daily_active_users_tenant_day
    ON daily_active_users_set (tenant_id, day);

CREATE TABLE weekly_active_users_set (
    tenant_id UUID NOT NULL,
    week_start DATE NOT NULL,
    user_key TEXT NOT NULL,
    PRIMARY KEY (tenant_id, week_start, user_key)
);

CREATE TABLE monthly_active_users_set (
    tenant_id UUID NOT NULL,
    month_start DATE NOT NULL,
    user_key TEXT NOT NULL,
    PRIMARY KEY (tenant_id, month_start, user_key)
);

CREATE TABLE daily_event_users (
    tenant_id UUID NOT NULL,
    day DATE NOT NULL,
    event_name TEXT NOT NULL,
    user_key TEXT NOT NULL,
    PRIMARY KEY (tenant_id, day, event_name, user_key)
);
CREATE INDEX idx_daily_event_users_tenant_day
    ON daily_event_users (tenant_id, day);
CREATE INDEX idx_daily_event_users_day_event
    ON daily_event_users (tenant_id, day, event_name);

CREATE TABLE aggregates_revenue_hourly (
    tenant_id UUID NOT NULL,
    bucket_start TIMESTAMPTZ NOT NULL,
    currency TEXT NOT NULL,
    revenue NUMERIC(18, 2) NOT NULL,
    orders BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, bucket_start, currency)
);
CREATE INDEX idx_agg_revenue_hourly_tenant_bucket
    ON aggregates_revenue_hourly (tenant_id, bucket_start);

CREATE TABLE aggregates_revenue_daily (
    tenant_id UUID NOT NULL,
    bucket_start DATE NOT NULL,
    currency TEXT NOT NULL,
    revenue NUMERIC(18, 2) NOT NULL,
    orders BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, bucket_start, currency)
);
CREATE INDEX idx_agg_revenue_daily_tenant_bucket
    ON aggregates_revenue_daily (tenant_id, bucket_start);

CREATE TABLE funnels_definitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name TEXT NOT NULL,
    steps JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE aggregates_funnel_runs_daily (
    tenant_id UUID NOT NULL,
    funnel_id UUID NOT NULL REFERENCES funnels_definitions(id),
    day DATE NOT NULL,
    step_index INT NOT NULL,
    users BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, funnel_id, day, step_index)
);

CREATE TABLE retention_definitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name TEXT NOT NULL,
    cohort_event TEXT NOT NULL,
    return_event TEXT NOT NULL,
    window_days INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE aggregates_retention_cohorts (
    tenant_id UUID NOT NULL,
    retention_id UUID NOT NULL REFERENCES retention_definitions(id),
    cohort_day DATE NOT NULL,
    day_offset INT NOT NULL,
    users BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, retention_id, cohort_day, day_offset)
);

CREATE TABLE cohort_users (
    tenant_id UUID NOT NULL,
    retention_id UUID NOT NULL REFERENCES retention_definitions(id),
    cohort_day DATE NOT NULL,
    user_key TEXT NOT NULL,
    PRIMARY KEY (tenant_id, retention_id, cohort_day, user_key)
);
CREATE INDEX idx_cohort_users_lookup
    ON cohort_users (tenant_id, retention_id, cohort_day);

CREATE TABLE return_users (
    tenant_id UUID NOT NULL,
    retention_id UUID NOT NULL REFERENCES retention_definitions(id),
    return_day DATE NOT NULL,
    user_key TEXT NOT NULL,
    PRIMARY KEY (tenant_id, retention_id, return_day, user_key)
);
CREATE INDEX idx_return_users_lookup
    ON return_users (tenant_id, retention_id, return_day);

CREATE TABLE events_registry (
    tenant_id UUID NOT NULL,
    event_name TEXT NOT NULL,
    latest_schema_version INT NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    sample_properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    total_seen BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, event_name)
);
CREATE INDEX idx_events_registry_tenant_last_seen
    ON events_registry (tenant_id, last_seen_at DESC);

CREATE TABLE report_exports (
    token UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    created_by TEXT,
    format TEXT NOT NULL,
    content TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_exports_tenant_expires
    ON report_exports (tenant_id, expires_at);

CREATE TABLE report_shares (
    token UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    payload JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_shares_tenant_expires
    ON report_shares (tenant_id, expires_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    actor TEXT,
    event_type TEXT NOT NULL,
    success BOOLEAN NOT NULL,
    ip TEXT,
    user_agent TEXT,
    correlation_id TEXT,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id),
    role_code TEXT NOT NULL,
    role_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, role_code)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    permission_code TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE OR REPLACE FUNCTION create_events_partition(p_day DATE)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    partition_name TEXT;
    start_ts TIMESTAMPTZ;
    end_ts TIMESTAMPTZ;
BEGIN
    partition_name := format('events_raw_%s', to_char(p_day, 'YYYYMMDD'));
    start_ts := p_day::timestamptz;
    end_ts := (p_day + INTERVAL '1 day')::timestamptz;

    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF events_raw FOR VALUES FROM (%L) TO (%L)', partition_name, start_ts, end_ts);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id, received_at DESC)', partition_name || '_tenant_received_idx', partition_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id, event_name, received_at DESC)', partition_name || '_tenant_event_received_idx', partition_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id, user_id, received_at DESC)', partition_name || '_tenant_user_received_idx', partition_name);
END;
$$;

CREATE OR REPLACE FUNCTION drop_events_partition(p_day DATE)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    partition_name TEXT;
BEGIN
    partition_name := format('events_raw_%s', to_char(p_day, 'YYYYMMDD'));
    EXECUTE format('DROP TABLE IF EXISTS %I', partition_name);
END;
$$;

SELECT create_events_partition(((now() AT TIME ZONE 'UTC')::date - 1)::date);
SELECT create_events_partition((now() AT TIME ZONE 'UTC')::date);
SELECT create_events_partition(((now() AT TIME ZONE 'UTC')::date + 1)::date);
