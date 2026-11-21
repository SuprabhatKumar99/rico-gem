CREATE TABLE IF NOT EXISTS rag_feedback (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES rag_conversations(id) ON DELETE CASCADE,
    message_id UUID REFERENCES rag_messages(id) ON DELETE SET NULL,
    helpful BOOLEAN NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rag_feedback_tenant_created ON rag_feedback(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_rag_feedback_conversation ON rag_feedback(conversation_id);
