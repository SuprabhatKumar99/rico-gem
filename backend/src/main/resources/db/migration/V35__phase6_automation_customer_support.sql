-- Phase 6: durable workflow outbox, customer conversation history, and support operations.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS custom_domain_challenge_hash varchar(64);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS custom_domain_challenge_created_at timestamptz;
-- Check and resolve any previously verified duplicate custom domains before migration.
CREATE UNIQUE INDEX IF NOT EXISTS ix_phase6_verified_custom_domain ON tenants(lower(custom_domain)) WHERE custom_domain IS NOT NULL AND custom_domain_verified=true;
-- Additive migration; never rewrite previously applied migrations.
ALTER TABLE knowledge_automation_rule ADD COLUMN IF NOT EXISTS condition_json jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE knowledge_automation_rule ADD COLUMN IF NOT EXISTS cooldown_minutes integer NOT NULL DEFAULT 0 CHECK (cooldown_minutes BETWEEN 0 AND 10080);
ALTER TABLE knowledge_automation_rule ADD COLUMN IF NOT EXISTS max_attempts integer NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 1 AND 12);
CREATE TABLE knowledge_automation_execution (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 rule_id uuid NOT NULL REFERENCES knowledge_automation_rule(id) ON DELETE CASCADE,
 event_key varchar(300) NOT NULL, trigger_type varchar(40) NOT NULL, action_type varchar(40) NOT NULL,
 target_article_id uuid, payload jsonb NOT NULL DEFAULT '{}'::jsonb,
 state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','RUNNING','SUCCEEDED','DEAD')),
 attempts integer NOT NULL DEFAULT 0, max_attempts integer NOT NULL DEFAULT 5,
 next_attempt_at timestamptz NOT NULL DEFAULT now(), locked_at timestamptz,
 last_error varchar(700), created_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz,
 UNIQUE(tenant_id,rule_id,event_key)
);
CREATE INDEX ix_phase6_execution_claim ON knowledge_automation_execution(state,next_attempt_at);
CREATE INDEX ix_phase6_execution_tenant ON knowledge_automation_execution(tenant_id,created_at DESC);
CREATE TABLE knowledge_automation_execution_step (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), execution_id uuid NOT NULL REFERENCES knowledge_automation_execution(id) ON DELETE CASCADE,
 tenant_id uuid NOT NULL REFERENCES tenants(id), attempted_at timestamptz NOT NULL DEFAULT now(),
 attempt integer NOT NULL, state varchar(20) NOT NULL, detail varchar(700)
);
CREATE INDEX ix_phase6_steps ON knowledge_automation_execution_step(tenant_id,execution_id,attempt);

CREATE TABLE support_conversations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 customer_id uuid NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
 language varchar(16) NOT NULL DEFAULT 'en', created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), escalated_ticket_id uuid REFERENCES support_tickets(id)
);
CREATE INDEX ix_phase6_conversations_customer ON support_conversations(tenant_id,customer_id,created_at DESC);
CREATE TABLE support_conversation_messages (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 conversation_id uuid NOT NULL REFERENCES support_conversations(id) ON DELETE CASCADE,
 role varchar(16) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
 content text NOT NULL, citation_slugs jsonb NOT NULL DEFAULT '[]'::jsonb,
 answer_grounded boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_phase6_messages ON support_conversation_messages(tenant_id,conversation_id,created_at);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS resolved_at timestamptz;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS escalated_at timestamptz;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS conversation_id uuid REFERENCES support_conversations(id);
CREATE INDEX ix_phase6_support_customer ON support_tickets(tenant_id,customer_id,created_at DESC);

CREATE TABLE article_translation_proposals (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 source_article_id uuid NOT NULL REFERENCES articles(id), target_language varchar(16) NOT NULL,
 translated_title varchar(500) NOT NULL, translated_content text NOT NULL,
 state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','APPROVED','REJECTED')),
 created_by uuid NOT NULL REFERENCES users(id), reviewed_by uuid REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now(), reviewed_at timestamptz
);
CREATE UNIQUE INDEX ix_phase6_translation_pending ON article_translation_proposals(tenant_id,source_article_id,target_language) WHERE state='PENDING';
CREATE INDEX ix_phase6_translation_proposals ON article_translation_proposals(tenant_id,state,created_at DESC);

DO $$ DECLARE name text;
BEGIN
 FOREACH name IN ARRAY ARRAY['knowledge_automation_execution','knowledge_automation_execution_step','support_conversations','support_conversation_messages','article_translation_proposals'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',name);
  EXECUTE format('CREATE POLICY %I ON %I USING (tenant_id=nullif(current_setting(''app.current_tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=nullif(current_setting(''app.current_tenant_id'',true),'''')::uuid)', name||'_tenant',name);
 END LOOP;
END $$;
