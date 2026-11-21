-- Dual-admin, time-limited MFA break-glass requests (no codes or MFA secrets stored here).
CREATE TABLE mfa_admin_recovery_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_user_id uuid NOT NULL REFERENCES users(id),
    requested_by uuid NOT NULL REFERENCES users(id),
    approved_by uuid REFERENCES users(id),
    external_ticket varchar(160) NOT NULL,
    reason varchar(500) NOT NULL,
    expires_at timestamptz NOT NULL,
    approved_at timestamptz,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT mfa_recovery_no_self_approval CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CONSTRAINT mfa_recovery_status CHECK (status IN ('PENDING', 'APPROVED', 'EXPIRED'))
);
CREATE INDEX idx_mfa_recovery_tenant_created ON mfa_admin_recovery_requests(tenant_id, created_at DESC);
CREATE INDEX idx_mfa_recovery_target ON mfa_admin_recovery_requests(tenant_id, target_user_id);
