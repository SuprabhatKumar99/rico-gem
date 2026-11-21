-- Customer experience: customer accounts, magic links, support handoff, domains and translation links.
CREATE TABLE IF NOT EXISTS customer_users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email varchar(320) NOT NULL,
    name varchar(255),
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    last_login_at timestamptz,
    UNIQUE(tenant_id,email)
);
CREATE INDEX IF NOT EXISTS idx_customer_users_tenant_email ON customer_users(tenant_id,email);

CREATE TABLE IF NOT EXISTS customer_magic_links (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id uuid NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_customer_magic_links_expiry ON customer_magic_links(expires_at);

CREATE TABLE IF NOT EXISTS customer_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id uuid NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS support_tickets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id uuid REFERENCES customer_users(id) ON DELETE SET NULL,
    email varchar(320) NOT NULL,
    subject varchar(500) NOT NULL,
    description text NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'OPEN',
    provider varchar(32) NOT NULL DEFAULT 'EMAIL',
    external_ticket_id varchar(255),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_support_tickets_tenant_created ON support_tickets(tenant_id,created_at);

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS custom_domain varchar(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS custom_domain_verified boolean NOT NULL DEFAULT false;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS custom_domain_ssl_status varchar(32) NOT NULL DEFAULT 'NOT_CONFIGURED';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS support_email varchar(320);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS supported_languages varchar(1000) NOT NULL DEFAULT 'en';

CREATE TABLE IF NOT EXISTS article_translation_links (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    source_article_id uuid NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    translated_article_id uuid NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    language varchar(16) NOT NULL,
    UNIQUE(source_article_id,translated_article_id),
    UNIQUE(source_article_id,language)
);
CREATE INDEX IF NOT EXISTS idx_article_translation_source ON article_translation_links(tenant_id,source_article_id);
