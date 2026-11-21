-- Phase 1 additive governance, review trail, threaded comments and reusable content
ALTER TABLE articles ADD COLUMN owner_id UUID REFERENCES users(id);
ALTER TABLE articles ADD COLUMN review_interval_days INTEGER NOT NULL DEFAULT 180 CHECK(review_interval_days BETWEEN 1 AND 3650);
ALTER TABLE articles ADD COLUMN next_review_at TIMESTAMPTZ;
ALTER TABLE articles ADD COLUMN verified_at TIMESTAMPTZ;
ALTER TABLE articles ADD COLUMN verified_by UUID REFERENCES users(id);
ALTER TABLE articles ADD COLUMN review_due_notified_at TIMESTAMPTZ;
UPDATE articles SET owner_id=created_by WHERE owner_id IS NULL;
ALTER TABLE article_block_comments ADD COLUMN parent_id UUID REFERENCES article_block_comments(id);
CREATE INDEX idx_phase1_due ON articles(tenant_id, next_review_at) WHERE next_review_at IS NOT NULL;
CREATE INDEX idx_phase1_comments_parent ON article_block_comments(tenant_id,parent_id);
CREATE TABLE article_review_decisions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 article_id UUID NOT NULL REFERENCES articles(id), version_id UUID NOT NULL REFERENCES article_versions(id),
 reviewer_id UUID NOT NULL REFERENCES users(id), decision VARCHAR(32) NOT NULL,
 comment TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_phase1_review_decisions ON article_review_decisions(tenant_id,article_id,created_at DESC);
CREATE TABLE article_verification_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 article_id UUID NOT NULL REFERENCES articles(id), actor_id UUID REFERENCES users(id),
 action VARCHAR(48) NOT NULL, details TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_phase1_verification_events ON article_verification_events(tenant_id,article_id,created_at DESC);
CREATE TABLE article_content_templates (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 template_key VARCHAR(120) NOT NULL,name VARCHAR(180) NOT NULL,description TEXT,
 content JSONB NOT NULL, created_by UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,template_key));
CREATE TABLE article_reusable_blocks (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),tenant_id UUID NOT NULL REFERENCES tenants(id),
 name VARCHAR(180) NOT NULL,block JSONB NOT NULL,created_by UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_phase1_reusable_blocks ON article_reusable_blocks(tenant_id,name);
-- New tenant content stores follow existing database isolation conventions.
ALTER TABLE article_review_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE article_verification_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE article_content_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE article_reusable_blocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_article_review_decisions ON article_review_decisions USING(tenant_id=current_setting('app.current_tenant_id',true)::uuid);
CREATE POLICY tenant_isolation_article_verification_events ON article_verification_events USING(tenant_id=current_setting('app.current_tenant_id',true)::uuid);
CREATE POLICY tenant_isolation_article_content_templates ON article_content_templates USING(tenant_id=current_setting('app.current_tenant_id',true)::uuid);
CREATE POLICY tenant_isolation_article_reusable_blocks ON article_reusable_blocks USING(tenant_id=current_setting('app.current_tenant_id',true)::uuid);

-- Historic scheduled versions created before approval gates must be explicitly re-approved.
-- Retain their content and review state; cancel the old publish timestamp rather than auto-publish.
UPDATE article_versions SET status='IN_REVIEW'
 WHERE status='SCHEDULED' AND reviewed_by IS NULL;
UPDATE articles a SET scheduled_publish_at=NULL
 WHERE scheduled_publish_at IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM article_versions v
      WHERE v.article_id=a.id AND v.tenant_id=a.tenant_id
      AND v.status='SCHEDULED' AND v.reviewed_by IS NOT NULL);
