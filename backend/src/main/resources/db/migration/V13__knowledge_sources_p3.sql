CREATE TABLE knowledge_sources (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    type VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    url TEXT,
    storage_key TEXT,
    mime_type VARCHAR(150),
    language VARCHAR(16) NOT NULL DEFAULT 'en',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sync_interval_minutes INTEGER,
    next_sync_at TIMESTAMPTZ,
    max_pages INTEGER NOT NULL DEFAULT 25,
    last_synced_at TIMESTAMPTZ,
    checksum VARCHAR(128),
    last_error TEXT,
    discovered_pages INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_knowledge_sources_tenant ON knowledge_sources(tenant_id);
CREATE INDEX idx_knowledge_sources_due ON knowledge_sources(status, next_sync_at);
CREATE UNIQUE INDEX uq_knowledge_sources_tenant_url ON knowledge_sources(tenant_id, url) WHERE url IS NOT NULL;

ALTER TABLE articles ADD COLUMN IF NOT EXISTS language VARCHAR(16) NOT NULL DEFAULT 'en';
CREATE INDEX IF NOT EXISTS idx_articles_tenant_language ON articles(tenant_id, language);
