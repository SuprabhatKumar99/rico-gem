CREATE TABLE article_intelligence (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    article_id uuid NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    version_id uuid REFERENCES article_versions(id) ON DELETE SET NULL,
    keywords jsonb NOT NULL DEFAULT '[]'::jsonb,
    topics jsonb NOT NULL DEFAULT '[]'::jsonb,
    health_score FLOAT NOT NULL DEFAULT 0,
    health_reasons jsonb NOT NULL DEFAULT '[]'::jsonb,
    analyzed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, article_id)
);

CREATE TABLE knowledge_gap_tickets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    query_text varchar(500) NOT NULL,
    volume integer NOT NULL DEFAULT 0,
    zero_result_count integer NOT NULL DEFAULT 0,
    unsuccessful_count integer NOT NULL DEFAULT 0,
    gap_score integer NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL DEFAULT 'OPEN',
    suggested_title varchar(300),
    suggested_outline jsonb NOT NULL DEFAULT '[]'::jsonb,
    suggested_keywords jsonb NOT NULL DEFAULT '[]'::jsonb,
    assigned_to uuid,
    article_id uuid REFERENCES articles(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, query_text)
);

CREATE INDEX idx_knowledge_gap_tenant_score ON knowledge_gap_tickets(tenant_id, gap_score DESC);
CREATE INDEX idx_knowledge_gap_status ON knowledge_gap_tickets(tenant_id, status);

CREATE TABLE article_feedback_classifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    analytics_event_id uuid NOT NULL REFERENCES analytics_events(id) ON DELETE CASCADE,
    article_id uuid REFERENCES articles(id) ON DELETE SET NULL,
    reason varchar(64) NOT NULL,
    confidence  FLOAT NOT NULL DEFAULT 0,
    rationale varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (analytics_event_id)
);

CREATE TABLE article_dependencies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    article_id uuid NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    depends_on_article_id uuid NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    dependency_type varchar(32) NOT NULL DEFAULT 'REFERENCE',
    evidence varchar(500),
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, article_id, depends_on_article_id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_article_dependency_target ON article_dependencies(tenant_id, depends_on_article_id);
