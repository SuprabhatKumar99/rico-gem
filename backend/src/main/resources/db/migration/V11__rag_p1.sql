CREATE TABLE rag_conversations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rag_conversations_tenant_updated ON rag_conversations(tenant_id, updated_at);

CREATE TABLE rag_messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    conversation_id UUID NOT NULL REFERENCES rag_conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rag_messages_conversation_created ON rag_messages(conversation_id, created_at);

CREATE TABLE rag_eval_cases (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    question TEXT NOT NULL,
    expected_article_ids TEXT NOT NULL,
    expected_answer TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rag_eval_cases_tenant ON rag_eval_cases(tenant_id);

CREATE TABLE rag_eval_runs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    case_count INT NOT NULL DEFAULT 0,
    hit_at_k DOUBLE PRECISION NOT NULL DEFAULT 0,
    mrr DOUBLE PRECISION NOT NULL DEFAULT 0,
    avg_latency_ms DOUBLE PRECISION NOT NULL DEFAULT 0
);
CREATE INDEX idx_rag_eval_runs_tenant_created ON rag_eval_runs(tenant_id, started_at);
