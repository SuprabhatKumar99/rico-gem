-- Phase 5: additive, tenant-bound AI operations, agents, graph and review proposals.
CREATE TABLE ai_usage_event (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 provider varchar(64) NOT NULL, model varchar(160) NOT NULL, operation varchar(60) NOT NULL,
 input_tokens bigint NOT NULL DEFAULT 0, output_tokens bigint NOT NULL DEFAULT 0,
 duration_ms bigint NOT NULL DEFAULT 0, success boolean NOT NULL,
 estimated_cost_usd numeric(18,8), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ai_usage_tenant_date ON ai_usage_event(tenant_id,created_at DESC);
CREATE TABLE ai_quota_counter (
 tenant_id uuid NOT NULL REFERENCES tenants(id), period varchar(7) NOT NULL,
 requests integer NOT NULL DEFAULT 0 CHECK(requests >= 0),
 PRIMARY KEY (tenant_id,period)
);
CREATE TABLE ai_agent_definition (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 name varchar(120) NOT NULL, instructions varchar(4000) NOT NULL,
 tool_name varchar(40) NOT NULL DEFAULT 'KNOWLEDGE_SEARCH',
 source_scope varchar(24) NOT NULL DEFAULT 'ARTICLES_ONLY' CHECK(source_scope IN ('ARTICLES_ONLY','ADMIN_CONNECTORS')),
 max_tool_calls integer NOT NULL DEFAULT 1 CHECK(max_tool_calls BETWEEN 1 AND 3),
 enabled boolean NOT NULL DEFAULT false, requires_approval boolean NOT NULL DEFAULT true,
 created_by uuid NOT NULL REFERENCES users(id), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ai_agent_tenant ON ai_agent_definition(tenant_id,updated_at DESC);
CREATE TABLE ai_agent_run (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 agent_id uuid NOT NULL REFERENCES ai_agent_definition(id), actor_id uuid NOT NULL REFERENCES users(id),
 input_text varchar(2000) NOT NULL, status varchar(24) NOT NULL,
 output_text text, citations jsonb NOT NULL DEFAULT '[]'::jsonb,
 tool_calls integer NOT NULL DEFAULT 0, approved_by uuid REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz
);
CREATE INDEX ai_agent_run_tenant ON ai_agent_run(tenant_id,created_at DESC);
CREATE TABLE ai_graph_edge (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 from_article_id uuid NOT NULL REFERENCES articles(id), to_article_id uuid NOT NULL REFERENCES articles(id),
 relation varchar(32) NOT NULL, confidence numeric(5,4) NOT NULL,
 reviewed boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,from_article_id,to_article_id,relation),
 CHECK(from_article_id <> to_article_id)
);
CREATE INDEX ai_graph_edge_tenant ON ai_graph_edge(tenant_id,from_article_id);
CREATE TABLE ai_graph_entity (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 article_id uuid NOT NULL REFERENCES articles(id), name varchar(120) NOT NULL,
 confidence numeric(5,4) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,article_id,name)
);
CREATE INDEX ai_graph_entity_tenant ON ai_graph_entity(tenant_id,name);
CREATE TABLE ai_quality_proposal (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 article_id uuid NOT NULL REFERENCES articles(id), related_article_id uuid REFERENCES articles(id),
 category varchar(40) NOT NULL, explanation varchar(1000) NOT NULL,
 score numeric(5,4) NOT NULL, status varchar(20) NOT NULL DEFAULT 'OPEN',
 created_at timestamptz NOT NULL DEFAULT now(), reviewed_by uuid REFERENCES users(id),
 reviewed_at timestamptz
);
CREATE INDEX ai_quality_tenant ON ai_quality_proposal(tenant_id,status,created_at DESC);
CREATE TABLE ai_eval_case (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 question varchar(300) NOT NULL, expected_article_id uuid REFERENCES articles(id),
 expected_phrase varchar(500), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ai_eval_result (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 case_id uuid NOT NULL REFERENCES ai_eval_case(id),
 hit boolean NOT NULL, cited boolean NOT NULL, grounded boolean NOT NULL,
 latency_ms bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ai_eval_result_tenant ON ai_eval_result(tenant_id,created_at DESC);
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['ai_usage_event','ai_quota_counter','ai_agent_definition','ai_agent_run','ai_graph_edge','ai_graph_entity','ai_quality_proposal','ai_eval_case','ai_eval_result'] LOOP
   EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
   EXECUTE format('CREATE POLICY %I ON %I USING (tenant_id = nullif(current_setting(''app.current_tenant_id'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.current_tenant_id'', true), '''')::uuid)',t||'_tenant',t);
 END LOOP;
END $$;
