-- Outbound webhooks (Phase 9), so external CRM/support tools can react to
-- content changes instead of polling the Agent API. Deliveries are logged
-- (not just fired-and-forgotten) so a tenant admin can see what was sent,
-- when, and whether it succeeded — this is the "did my integration actually
-- receive that event" debugging surface.

CREATE TABLE webhook_subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    url          VARCHAR(2048) NOT NULL,
    secret       VARCHAR(255) NOT NULL, -- used to HMAC-sign delivered payloads; never returned in API responses after creation
    event_types  JSONB NOT NULL,        -- e.g. ["ARTICLE_PUBLISHED", "ARTICLE_ARCHIVED"]
    active       BOOLEAN NOT NULL DEFAULT true,
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    event_type      VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    attempt_count   INT NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at    TIMESTAMPTZ
);

CREATE INDEX idx_webhook_subscriptions_tenant ON webhook_subscriptions(tenant_id);
CREATE INDEX idx_webhook_deliveries_tenant ON webhook_deliveries(tenant_id);
CREATE INDEX idx_webhook_deliveries_subscription ON webhook_deliveries(subscription_id, created_at);
