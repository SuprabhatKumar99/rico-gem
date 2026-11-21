-- Defense in depth: an application bug must not attach another tenant's
-- target, requesting admin or approving admin to a recovery request.
ALTER TABLE users ADD CONSTRAINT uq_users_tenant_id_id UNIQUE (tenant_id, id);
ALTER TABLE mfa_admin_recovery_requests
    ADD CONSTRAINT fk_mfa_recovery_target_tenant
        FOREIGN KEY (tenant_id, target_user_id) REFERENCES users(tenant_id, id),
    ADD CONSTRAINT fk_mfa_recovery_requester_tenant
        FOREIGN KEY (tenant_id, requested_by) REFERENCES users(tenant_id, id),
    ADD CONSTRAINT fk_mfa_recovery_approver_tenant
        FOREIGN KEY (tenant_id, approved_by) REFERENCES users(tenant_id, id);
