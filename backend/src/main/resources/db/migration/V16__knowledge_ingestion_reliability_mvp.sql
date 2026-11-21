-- Sprint 2: knowledge ingestion reliability
ALTER TABLE knowledge_sources
    ADD COLUMN IF NOT EXISTS processing_phase VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_retries INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_knowledge_sources_tenant_status
    ON knowledge_sources(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_knowledge_sources_retry
    ON knowledge_sources(status, next_sync_at);
