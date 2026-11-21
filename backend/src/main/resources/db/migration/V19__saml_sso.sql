-- Production SAML SP configuration.
-- V18 introduced the generic SSO connection and JSON settings. V19 promotes
-- the SAML trust/configuration fields into typed columns so the Spring
-- Security SAML registration can be built deterministically and validated by
-- the database-backed tenant configuration.

ALTER TABLE sso_connections
    ADD COLUMN IF NOT EXISTS saml_idp_entity_id text,
    ADD COLUMN IF NOT EXISTS saml_idp_sso_url text,
    ADD COLUMN IF NOT EXISTS saml_idp_certificate text,
    ADD COLUMN IF NOT EXISTS saml_sp_entity_id text,
    ADD COLUMN IF NOT EXISTS saml_want_authn_requests_signed boolean NOT NULL DEFAULT false;

-- Backfill from the V18 JSON representation when an installation already has
-- SAML configured. Existing explicit columns always win on re-runs.
UPDATE sso_connections
SET saml_idp_entity_id = COALESCE(NULLIF(saml_idp_entity_id, ''), settings_json ->> 'idpEntityId'),
    saml_idp_sso_url = COALESCE(NULLIF(saml_idp_sso_url, ''), settings_json ->> 'idpSsoUrl'),
    saml_idp_certificate = COALESCE(NULLIF(saml_idp_certificate, ''), settings_json ->> 'idpCertificate'),
    saml_sp_entity_id = COALESCE(NULLIF(saml_sp_entity_id, ''), settings_json ->> 'spEntityId'),
    saml_want_authn_requests_signed = CASE
        WHEN lower(COALESCE(settings_json ->> 'wantAuthnRequestsSigned', '')) IN ('true', 'false')
            THEN (settings_json ->> 'wantAuthnRequestsSigned')::boolean
        ELSE saml_want_authn_requests_signed
    END
WHERE provider = 'SAML';

CREATE INDEX IF NOT EXISTS idx_sso_connections_saml_enabled
    ON sso_connections(tenant_id, enabled)
    WHERE provider = 'SAML';

-- Keep the generic JSON configuration for backwards compatibility and future
-- IdP-specific options. The typed columns above are the authoritative values
-- used by the SAML runtime when present.
