CREATE TABLE IF NOT EXISTS oauth_connector_connection (
 tenant_id uuid NOT NULL,
 provider varchar(40) NOT NULL,
 encrypted_token text NOT NULL,
 connected_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,provider)
);
