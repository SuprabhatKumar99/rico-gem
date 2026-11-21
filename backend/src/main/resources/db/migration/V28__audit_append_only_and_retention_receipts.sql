-- Database-level immutability: application entity callbacks alone are not sufficient.
CREATE TABLE audit_retention_receipts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    purged_before timestamptz NOT NULL,
    deleted_count integer NOT NULL CHECK (deleted_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_retention_receipts_tenant ON audit_retention_receipts(tenant_id, created_at DESC);

CREATE OR REPLACE FUNCTION ricoz_guard_audit_changes() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('ricoz.audit_retention_mode', true) = 'purge' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_logs are append-only (retention deletes require the controlled purge transaction)';
END;
$$;
CREATE TRIGGER trg_audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION ricoz_guard_audit_changes();

CREATE OR REPLACE FUNCTION ricoz_guard_audit_receipts() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit retention receipts are append-only';
END;
$$;
CREATE TRIGGER trg_audit_retention_receipts_immutable BEFORE UPDATE OR DELETE ON audit_retention_receipts
    FOR EACH ROW EXECUTE FUNCTION ricoz_guard_audit_receipts();

-- NOTE: the retention marker is transaction-local, not a substitute for DB role
-- separation. Keep application DB credentials away from end users and operators.
