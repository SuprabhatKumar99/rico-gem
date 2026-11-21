-- Phase 7: additive, tenant-isolated SaaS plans and Stripe event reconciliation.
-- Existing tenants retain a non-public unlimited legacy tier. Never silently bill them.
CREATE TABLE saas_plan (
 code varchar(32) PRIMARY KEY, name varchar(90) NOT NULL,
 monthly_price_minor bigint NOT NULL DEFAULT 0 CHECK(monthly_price_minor>=0), currency varchar(3) NOT NULL DEFAULT 'usd',
 stripe_price_id varchar(150) UNIQUE,
 max_seats integer NOT NULL DEFAULT 0 CHECK(max_seats>=0),
 max_articles integer NOT NULL DEFAULT 0 CHECK(max_articles>=0),
 max_connectors integer NOT NULL DEFAULT 0 CHECK(max_connectors>=0),
 max_ai_requests integer NOT NULL DEFAULT 0 CHECK(max_ai_requests>=0),
 visible boolean NOT NULL DEFAULT true, active boolean NOT NULL DEFAULT true,
 features jsonb NOT NULL DEFAULT '{}'::jsonb,
 created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO saas_plan(code,name,monthly_price_minor,max_seats,max_articles,max_connectors,max_ai_requests,visible,features) VALUES
 ('FREE','Free',0,3,100,0,100,true,'{"analytics":true,"trial":false}'::jsonb),
 ('GROWTH','Growth',2900,15,2000,3,2000,true,'{"analytics":true,"ai":true}'::jsonb),
 ('BUSINESS','Business',9900,100,20000,15,20000,true,'{"analytics":true,"ai":true,"advanced":true}'::jsonb),
 ('ENTERPRISE','Enterprise',0,0,0,0,0,true,'{"contactSales":true,"sso":true}'::jsonb),
 ('LEGACY','Existing customer',0,0,0,0,0,false,'{"grandfathered":true}'::jsonb);
-- Price IDs intentionally NULL: configure via secured deployment SQL, never assume live Stripe prices.
CREATE TABLE saas_subscription (
 tenant_id uuid PRIMARY KEY REFERENCES tenants(id), plan_code varchar(32) NOT NULL REFERENCES saas_plan(code),
 status varchar(24) NOT NULL CHECK(status IN ('FREE','TRIALING','ACTIVE','PAST_DUE','UNPAID','INCOMPLETE','CANCELED','LEGACY')),
 provider varchar(24) NOT NULL DEFAULT 'NONE', provider_customer_id varchar(180) UNIQUE,
 provider_subscription_id varchar(180) UNIQUE, current_period_end timestamptz,
 trial_ends_at timestamptz, cancel_at_period_end boolean NOT NULL DEFAULT false,
 last_event_created bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO saas_subscription(tenant_id,plan_code,status)
 SELECT id,'LEGACY','LEGACY' FROM tenants ON CONFLICT(tenant_id) DO NOTHING;
CREATE TABLE saas_checkout_attempt (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 plan_code varchar(32) NOT NULL REFERENCES saas_plan(code), provider_session_id varchar(180) NOT NULL UNIQUE,
 created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz
);
CREATE INDEX ix_saas_checkout_tenant ON saas_checkout_attempt(tenant_id,created_at DESC);
CREATE TABLE saas_billing_event (
 provider varchar(24) NOT NULL, provider_event_id varchar(200) NOT NULL,
 event_type varchar(110) NOT NULL, tenant_id uuid REFERENCES tenants(id),
 event_created bigint NOT NULL, processed_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(provider,provider_event_id)
);
CREATE INDEX ix_saas_billing_event_tenant ON saas_billing_event(tenant_id,processed_at DESC);
CREATE TABLE saas_invoice (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id),
 provider_invoice_id varchar(180) NOT NULL UNIQUE, provider_subscription_id varchar(180),
 status varchar(32) NOT NULL, currency varchar(3) NOT NULL,
 amount_due_minor bigint NOT NULL DEFAULT 0, amount_paid_minor bigint NOT NULL DEFAULT 0,
 hosted_invoice_url text, invoice_pdf_url text, paid_at timestamptz, last_event_created bigint NOT NULL DEFAULT 0, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_saas_invoice_tenant ON saas_invoice(tenant_id,updated_at DESC);
-- PostgreSQL tenant-context RLS defense-in-depth (super-admin global view has dedicated SECURITY DEFINER strategy outside these tables).
DO $$ DECLARE n text; BEGIN
 FOREACH n IN ARRAY ARRAY['saas_subscription','saas_checkout_attempt','saas_invoice'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',n);
  EXECUTE format('CREATE POLICY %I ON %I USING (tenant_id=nullif(current_setting(''app.current_tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=nullif(current_setting(''app.current_tenant_id'',true),'''')::uuid)', n||'_tenant',n);
 END LOOP;
END $$;
