-- Tenant-aware persisted automation rules. No implicit background execution.
CREATE TABLE knowledge_automation_rule (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL,
 name varchar(120) NOT NULL,
 trigger_type varchar(40) NOT NULL CHECK(trigger_type IN ('ARTICLE_CREATED','ARTICLE_UPDATED','REVIEW_OVERDUE','CONNECTOR_FAILED')),
 action_type varchar(40) NOT NULL CHECK(action_type IN ('FLAG_FOR_REVIEW','QUEUE_NOTIFICATION','AUDIT_ONLY')),
 enabled boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_knowledge_automation_tenant ON knowledge_automation_rule(tenant_id,created_at DESC);
