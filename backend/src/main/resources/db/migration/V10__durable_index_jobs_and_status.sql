-- Durable indexing jobs and per-article search/RAG synchronization status.
ALTER TABLE articles
    ADD COLUMN search_index_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN rag_index_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN search_indexed_at TIMESTAMPTZ,
    ADD COLUMN rag_indexed_at TIMESTAMPTZ,
    ADD COLUMN search_index_error TEXT,
    ADD COLUMN rag_index_error TEXT;

CREATE INDEX idx_articles_tenant_search_index_status
    ON articles(tenant_id, search_index_status);
CREATE INDEX idx_articles_tenant_rag_index_status
    ON articles(tenant_id, rag_index_status);

CREATE TABLE index_jobs (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    article_id      UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    version_id      UUID REFERENCES article_versions(id) ON DELETE SET NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count   INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 5,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_index_jobs_claim
    ON index_jobs(status, next_attempt_at, created_at);
CREATE INDEX idx_index_jobs_tenant_article
    ON index_jobs(tenant_id, article_id, created_at);
