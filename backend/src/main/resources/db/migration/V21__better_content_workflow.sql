-- Better content workflow: metadata, block review comments, scheduling and notifications
ALTER TABLE articles ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE articles ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE articles ADD COLUMN IF NOT EXISTS scheduled_publish_at TIMESTAMPTZ;
ALTER TABLE articles ADD COLUMN IF NOT EXISTS scheduled_unpublish_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS article_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    tag VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_article_tag UNIQUE (tenant_id, article_id, tag)
);
CREATE INDEX IF NOT EXISTS idx_article_tags_tenant_tag ON article_tags(tenant_id, tag);

CREATE TABLE IF NOT EXISTS article_related (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    related_article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_article_related UNIQUE (tenant_id, article_id, related_article_id),
    CONSTRAINT chk_article_related_not_self CHECK (article_id <> related_article_id)
);

CREATE TABLE IF NOT EXISTS article_block_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    version_id UUID NOT NULL REFERENCES article_versions(id) ON DELETE CASCADE,
    block_id VARCHAR(100) NOT NULL,
    author_id UUID NOT NULL REFERENCES users(id),
    comment TEXT NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_block_comments_version ON article_block_comments(tenant_id, article_id, version_id);

CREATE TABLE IF NOT EXISTS in_app_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(120) NOT NULL,
    title VARCHAR(300) NOT NULL,
    message TEXT NOT NULL,
    article_id UUID REFERENCES articles(id) ON DELETE CASCADE,
    version_id UUID REFERENCES article_versions(id) ON DELETE CASCADE,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON in_app_notifications(tenant_id,user_id,read,created_at DESC);

ALTER TABLE article_versions DROP CONSTRAINT IF EXISTS article_versions_status_check;

ALTER TABLE article_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE article_related ENABLE ROW LEVEL SECURITY;
ALTER TABLE article_block_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE in_app_notifications ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_article_tags ON article_tags USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_article_related ON article_related USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_article_block_comments ON article_block_comments USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_in_app_notifications ON in_app_notifications USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
