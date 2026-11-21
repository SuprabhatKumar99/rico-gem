-- Phase 4: the Yjs update is authoritative, the article-version JSON is an auditable/readable projection.
-- Keep published ArticleVersion records immutable; snapshots only attach to editable versions.
CREATE TABLE article_collaboration_state (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  article_id uuid NOT NULL REFERENCES articles(id),
  version_id uuid NOT NULL REFERENCES article_versions(id),
  ydoc_state bytea NOT NULL,
  mirrored_sha256 char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT article_collab_version_unique UNIQUE(tenant_id,version_id),
  CONSTRAINT article_collab_size CHECK(octet_length(ydoc_state) BETWEEN 1 AND 4194304)
);
CREATE INDEX idx_article_collab_tenant_article ON article_collaboration_state(tenant_id,article_id);
ALTER TABLE article_collaboration_state ENABLE ROW LEVEL SECURITY;
CREATE POLICY article_collaboration_tenant_policy ON article_collaboration_state
 USING (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid)
 WITH CHECK (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid);
-- Do not create a separate competing article-version table: version snapshots remain in article_versions.
