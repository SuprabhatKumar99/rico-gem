-- Phase 8: never persist raw bearer keys or full query/prompt contents.
CREATE TABLE mcp_api_usage (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    api_key_id UUID NOT NULL REFERENCES rag_api_keys(id) ON DELETE CASCADE,
    method VARCHAR(80) NOT NULL, tool_name VARCHAR(80), success BOOLEAN NOT NULL,
    latency_ms BIGINT NOT NULL CHECK(latency_ms>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mcp_usage_tenant_time ON mcp_api_usage(tenant_id,created_at DESC);
CREATE INDEX idx_mcp_usage_key_time ON mcp_api_usage(api_key_id,created_at DESC);
CREATE TABLE mcp_review_requests (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    api_key_id UUID NOT NULL REFERENCES rag_api_keys(id) ON DELETE CASCADE,
    reason VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
       CHECK(status IN ('OPEN','RESOLVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_mcp_review_tenant_status ON mcp_review_requests(tenant_id,status,created_at DESC);

-- Defense in depth mirrors the tenant RLS model used in earlier phases.
ALTER TABLE mcp_api_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE mcp_review_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY mcp_api_usage_tenant ON mcp_api_usage
    USING (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid)
    WITH CHECK (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid);
CREATE POLICY mcp_review_requests_tenant ON mcp_review_requests
    USING (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid)
    WITH CHECK (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid);
