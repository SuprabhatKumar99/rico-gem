ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_secret varchar(128);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS device_id varchar(128);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS device_name varchar(255);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS user_agent text;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS ip_address varchar(64);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS last_used_at timestamptz;

CREATE TABLE IF NOT EXISTS tenant_settings (
    tenant_id uuid PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    timezone varchar(64) NOT NULL DEFAULT 'UTC',
    default_language varchar(16) NOT NULL DEFAULT 'en',
    max_users integer NOT NULL DEFAULT 100,
    max_storage_bytes bigint NOT NULL DEFAULT 10737418240,
    max_api_requests_monthly bigint NOT NULL DEFAULT 100000,
    max_rag_queries_monthly bigint NOT NULL DEFAULT 25000,
    max_ai_cost_cents_monthly bigint NOT NULL DEFAULT 100000,
    settings_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS usage_meters (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    metric varchar(64) NOT NULL,
    period_start date NOT NULL,
    quantity numeric(20,6) NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, metric, period_start)
);
CREATE INDEX IF NOT EXISTS idx_usage_meters_tenant_period ON usage_meters(tenant_id, period_start);

CREATE TABLE IF NOT EXISTS ai_cost_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider varchar(64) NOT NULL,
    model varchar(128) NOT NULL,
    operation varchar(64) NOT NULL,
    input_tokens bigint NOT NULL DEFAULT 0,
    output_tokens bigint NOT NULL DEFAULT 0,
    embedding_tokens bigint NOT NULL DEFAULT 0,
    cost_micros bigint NOT NULL DEFAULT 0,
    request_id varchar(128),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_cost_events_tenant_created ON ai_cost_events(tenant_id, created_at);

CREATE TABLE IF NOT EXISTS webhook_dead_letters (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    delivery_id uuid NOT NULL,
    subscription_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    reason text,
    attempt_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    replayed_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_webhook_dlq_tenant_created ON webhook_dead_letters(tenant_id, created_at);

ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS ip_address varchar(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent text;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS request_id varchar(128);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS source varchar(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS metadata jsonb;
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_created ON audit_logs(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_actor ON audit_logs(tenant_id, actor_user_id);
