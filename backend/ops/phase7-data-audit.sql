-- Run in staging as a database operator after applying V36.
SELECT plan_code, status, count(*) AS tenants FROM saas_subscription GROUP BY 1,2 ORDER BY 1,2;
SELECT code, visible, (stripe_price_id IS NOT NULL) AS stripe_ready FROM saas_plan ORDER BY code;
SELECT provider, event_type, count(*) FROM saas_billing_event GROUP BY 1,2 ORDER BY 1,2;
SELECT currency,status,count(*),sum(amount_paid_minor) FROM saas_invoice GROUP BY 1,2 ORDER BY 1,2;
-- Should return zero rows: unlinked/mismatched provider invoices.
SELECT i.id FROM saas_invoice i LEFT JOIN saas_subscription s ON s.tenant_id=i.tenant_id
 WHERE s.tenant_id IS NULL;
