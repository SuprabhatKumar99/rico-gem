-- Advanced search metadata, saved searches and alert state
ALTER TABLE articles ADD COLUMN IF NOT EXISTS article_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL';
CREATE INDEX IF NOT EXISTS idx_articles_tenant_type ON articles(tenant_id, article_type);

CREATE TABLE IF NOT EXISTS saved_searches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(320) NOT NULL,
    query_text VARCHAR(500) NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    language VARCHAR(16),
    tag VARCHAR(80),
    article_type VARCHAR(32),
    date_from TIMESTAMPTZ,
    date_to TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_checked_at TIMESTAMPTZ,
    last_notified_article_id UUID,
    last_notified_source_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_saved_searches_due ON saved_searches(active, last_checked_at);
CREATE INDEX IF NOT EXISTS idx_saved_searches_tenant ON saved_searches(tenant_id);

ALTER TABLE saved_searches ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_saved_searches ON saved_searches
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

ALTER TABLE analytics_events ADD COLUMN IF NOT EXISTS user_id UUID;
CREATE INDEX IF NOT EXISTS idx_analytics_events_user_clicks
    ON analytics_events(tenant_id, user_id, event_type, occurred_at DESC);
