#!/usr/bin/env bash
set -euo pipefail
# Requires ADMIN_ACCESS_TOKEN and TENANT_BACKEND_URL, uses no secrets in URLs.
: "${ADMIN_ACCESS_TOKEN:?Provide a staging ADMIN bearer token}"
BASE="${TENANT_BACKEND_URL:-http://localhost:8080}"
auth=(-H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}")
curl --fail-with-body -sS "${auth[@]}" "${BASE}/api/v1/billing/plans" >/dev/null
curl --fail-with-body -sS "${auth[@]}" "${BASE}/api/v1/billing/overview" >/dev/null
curl --fail-with-body -sS "${auth[@]}" "${BASE}/api/v1/billing/invoices" >/dev/null
curl --fail-with-body -sS "${auth[@]}" "${BASE}/api/v1/analytics/executive?days=30" >/dev/null
curl --fail-with-body -sS "${auth[@]}" "${BASE}/api/v1/analytics/executive/daily?days=30" >/dev/null
printf '%s\n' 'Phase 7 read-only tenant API smoke checks completed.'
