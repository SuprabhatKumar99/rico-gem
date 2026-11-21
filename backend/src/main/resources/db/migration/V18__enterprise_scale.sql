-- Enterprise scale: SSO/SCIM, groups, privacy, network controls, experimentation.
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS retention_days integer NOT NULL DEFAULT 3650;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS pii_redaction_enabled boolean NOT NULL DEFAULT true;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS ai_provider varchar(128);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS ai_region varchar(64);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS ip_allowlist text;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS oidc_config jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS saml_config jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE rag_api_keys ADD COLUMN IF NOT EXISTS ip_allowlist text;

ALTER TABLE roles ADD COLUMN IF NOT EXISTS permissions text NOT NULL DEFAULT '';
ALTER TABLE roles ADD COLUMN IF NOT EXISTS custom boolean NOT NULL DEFAULT false;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS custom_name varchar(150);
UPDATE roles SET custom = false WHERE custom IS NULL;

CREATE TABLE IF NOT EXISTS sso_connections (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider varchar(16) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    client_id varchar(255),
    client_secret_encrypted text,
    issuer_url text,
    authorization_url text,
    token_url text,
    userinfo_url text,
    metadata_url text,
    entity_id text,
    settings_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sso_connection_tenant_provider ON sso_connections(tenant_id, provider);

CREATE TABLE IF NOT EXISTS scim_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name varchar(150) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    token_prefix varchar(32) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    last_used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS groups (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name varchar(150) NOT NULL,
    external_id varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, name)
);
CREATE TABLE IF NOT EXISTS group_memberships (
    group_id uuid NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY(group_id,user_id)
);
ALTER TABLE articles ADD COLUMN IF NOT EXISTS visibility_group_id uuid REFERENCES groups(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_articles_visibility_group ON articles(tenant_id, visibility_group_id);

CREATE TABLE IF NOT EXISTS privacy_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_type varchar(16) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    requested_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    result_json jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_export_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    requested_by uuid,
    from_at timestamptz,
    to_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ab_experiments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    key varchar(120) NOT NULL,
    name varchar(255) NOT NULL,
    target varchar(32) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    traffic_percent integer NOT NULL DEFAULT 100,
    variants_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(tenant_id,key)
);
CREATE TABLE IF NOT EXISTS ab_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    experiment_id uuid NOT NULL REFERENCES ab_experiments(id) ON DELETE CASCADE,
    subject_key varchar(255) NOT NULL,
    variant_key varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(experiment_id,subject_key)
);
CREATE INDEX IF NOT EXISTS idx_ab_assignments_tenant_experiment ON ab_assignments(tenant_id,experiment_id);

CREATE TABLE IF NOT EXISTS api_key_ip_allowlists (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    rag_api_key_id uuid NOT NULL REFERENCES rag_api_keys(id) ON DELETE CASCADE,
    cidr varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(rag_api_key_id,cidr)
);

ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS retention_until timestamptz;
CREATE INDEX IF NOT EXISTS idx_audit_logs_retention ON audit_logs(tenant_id, retention_until);
