-- Single-use hashed recovery codes. Never store plaintext recovery codes.
CREATE TABLE mfa_recovery_codes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_mfa_recovery_code UNIQUE (tenant_id, user_id, code_hash)
);
CREATE INDEX idx_mfa_recovery_user ON mfa_recovery_codes(tenant_id, user_id);
