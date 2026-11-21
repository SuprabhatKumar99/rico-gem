-- Phase 2 connector marketplace: additive tenant-owned operational state.
ALTER TABLE oauth_connector_connection ADD COLUMN IF NOT EXISTS expires_at timestamptz;
ALTER TABLE oauth_connector_connection ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE oauth_connector_connection ADD COLUMN IF NOT EXISTS account_label varchar(300);

CREATE TABLE IF NOT EXISTS connector_oauth_state (
 state_hash char(64) PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), provider varchar(40) NOT NULL,
 verifier text, created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_connector_oauth_state_expiry ON connector_oauth_state(expires_at);

CREATE TABLE IF NOT EXISTS connector_source (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), provider varchar(40) NOT NULL,
 remote_id varchar(800) NOT NULL, display_name varchar(400) NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'PENDING',
 last_synced_at timestamptz, next_sync_at timestamptz NOT NULL DEFAULT now(),
 sync_interval_minutes integer NOT NULL DEFAULT 60 CHECK(sync_interval_minutes BETWEEN 15 AND 10080),
 retry_count integer NOT NULL DEFAULT 0, max_retries integer NOT NULL DEFAULT 5,
 last_error text, disabled boolean NOT NULL DEFAULT false,
 updated_at timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id, provider, remote_id), UNIQUE(id,tenant_id)
);
CREATE INDEX IF NOT EXISTS idx_connector_source_due ON connector_source(next_sync_at) WHERE disabled=false;
CREATE INDEX IF NOT EXISTS idx_connector_source_tenant ON connector_source(tenant_id,provider);

-- Access is restricted by default. Only the connector service's tenant/ACL-aware API
-- is permitted to return content. Never copy these documents into global RAG/search
-- indexes until the permission-aware Phase 3 retrieval gate is in place.
CREATE TABLE IF NOT EXISTS connector_document (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, source_id uuid NOT NULL,
 remote_id varchar(800) NOT NULL, title varchar(700) NOT NULL,
 source_url text, mime_type varchar(150), remote_modified_at timestamptz,
 checksum char(64) NOT NULL, content_text text NOT NULL,
 acl_mode varchar(32) NOT NULL DEFAULT 'ADMIN_ONLY',
 acl_principals jsonb NOT NULL DEFAULT '[]'::jsonb,
 acl_synced_at timestamptz,
 deleted_at timestamptz, last_seen_at timestamptz NOT NULL DEFAULT now(),
 search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title,'') || ' ' || left(coalesce(content_text,''),100000))) STORED,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,source_id,remote_id),
 FOREIGN KEY(source_id,tenant_id) REFERENCES connector_source(id,tenant_id) ON DELETE CASCADE,
 CHECK (acl_mode IN ('ADMIN_ONLY','PRINCIPALS'))
);
CREATE INDEX IF NOT EXISTS idx_connector_document_search ON connector_document USING gin(search_vector);
CREATE INDEX IF NOT EXISTS idx_connector_document_tenant ON connector_document(tenant_id,source_id,deleted_at);

CREATE TABLE IF NOT EXISTS connector_sync_run (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, source_id uuid NOT NULL,
 started_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz,
 state varchar(24) NOT NULL DEFAULT 'RUNNING', inserted_count integer NOT NULL DEFAULT 0,
 updated_count integer NOT NULL DEFAULT 0, deleted_count integer NOT NULL DEFAULT 0,
 error_message text,
 FOREIGN KEY(source_id,tenant_id) REFERENCES connector_source(id,tenant_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_connector_sync_run_tenant ON connector_sync_run(tenant_id,source_id,started_at DESC);

-- Database RLS is defense in depth, alongside parameterized tenant-qualified SQL.
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['connector_source','connector_document','connector_sync_run'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('DROP POLICY IF EXISTS %I ON %I',t||'_tenant_policy',t);
  EXECUTE format('CREATE POLICY %I ON %I USING (tenant_id = nullif(current_setting(''app.current_tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.current_tenant_id'',true),'''')::uuid)',t||'_tenant_policy',t);
 END LOOP;
END $$;
-- Application's JDBC connection must set app.current_tenant_id for RLS enforcement or run
-- with the privileged database owner. All application SQL also specifies tenant_id.

CREATE TABLE IF NOT EXISTS connector_webhook_delivery (
 tenant_id uuid NOT NULL REFERENCES tenants(id), provider varchar(40) NOT NULL, delivery_id varchar(150) NOT NULL,
 seen_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(tenant_id,provider,delivery_id)
);
CREATE INDEX IF NOT EXISTS idx_connector_delivery_age ON connector_webhook_delivery(seen_at);
