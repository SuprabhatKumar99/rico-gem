-- Phase 3: additive, privacy-preserving enterprise retrieval analytics.
-- Raw search queries and content are NEVER stored in this table.
CREATE TABLE enterprise_search_event (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenants(id),
 actor_id uuid NOT NULL,
 query_hash char(64) NOT NULL,
 result_count integer NOT NULL DEFAULT 0 CHECK(result_count>=0),
 article_count integer NOT NULL DEFAULT 0 CHECK(article_count>=0),
 connector_count integer NOT NULL DEFAULT 0 CHECK(connector_count>=0),
 latency_ms bigint NOT NULL DEFAULT 0 CHECK(latency_ms>=0),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_enterprise_search_tenant_day ON enterprise_search_event(tenant_id,created_at DESC);
CREATE INDEX idx_enterprise_search_unanswered ON enterprise_search_event(tenant_id,query_hash) WHERE result_count=0;
ALTER TABLE enterprise_search_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY enterprise_search_event_tenant_policy ON enterprise_search_event
 USING (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid)
 WITH CHECK(tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid);
-- Application SQL uses explicit tenant predicates in addition to RLS. Validate owner/bypass roles in staging.

CREATE TABLE enterprise_search_eval_case (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenants(id),
 question varchar(300) NOT NULL,
 expected_ids jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT enterprise_search_eval_ids_array CHECK (jsonb_typeof(expected_ids)='array')
);
CREATE INDEX idx_enterprise_eval_tenant ON enterprise_search_eval_case(tenant_id,created_at DESC);
ALTER TABLE enterprise_search_eval_case ENABLE ROW LEVEL SECURITY;
CREATE POLICY enterprise_search_eval_case_tenant_policy ON enterprise_search_eval_case
 USING (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid)
 WITH CHECK(tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid);

CREATE TABLE enterprise_search_feedback (
 tenant_id uuid NOT NULL REFERENCES tenants(id),
 actor_id uuid NOT NULL,
 hit_id varchar(36) NOT NULL,
 source_type varchar(40) NOT NULL,
 click_count integer NOT NULL DEFAULT 1 CHECK (click_count BETWEEN 1 AND 10000),
 last_clicked_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,actor_id,hit_id)
);
CREATE INDEX idx_enterprise_feedback_user ON enterprise_search_feedback(tenant_id,actor_id);
ALTER TABLE enterprise_search_feedback ENABLE ROW LEVEL SECURITY;
CREATE POLICY enterprise_search_feedback_tenant_policy ON enterprise_search_feedback
 USING (tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid)
 WITH CHECK(tenant_id=nullif(current_setting('app.current_tenant_id',true),'')::uuid);

-- RAG histories previously isolated only by tenant, so separate API keys could read them.
-- Legacy conversations remain unowned/inaccessible until an administrator migrates ownership
-- using independently verified evidence; never automatically claim them for the first key.
ALTER TABLE rag_conversations ADD COLUMN IF NOT EXISTS owner_key_id uuid;
CREATE INDEX idx_rag_conversation_tenant_owner ON rag_conversations(tenant_id,owner_key_id,updated_at DESC);
