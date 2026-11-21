-- Social identities bind provider issuer+subject to a tenant-local account. Never link by email alone.
CREATE TABLE social_identities (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 provider VARCHAR(16) NOT NULL CHECK (provider IN ('google','microsoft')),
 issuer VARCHAR(255) NOT NULL,
 subject VARCHAR(255) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id, provider, issuer, subject),
 UNIQUE(tenant_id, provider, user_id)
);
CREATE INDEX ix_social_identities_user ON social_identities(tenant_id, user_id);
ALTER TABLE social_identities ENABLE ROW LEVEL SECURITY;
CREATE POLICY social_identities_tenant ON social_identities
  USING (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid)
  WITH CHECK (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid);
