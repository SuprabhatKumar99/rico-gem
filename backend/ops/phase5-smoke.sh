#!/usr/bin/env bash
set -euo pipefail
: "${RICOZ_API_BASE:?Set API base URL such as http://localhost:8080}"
: "${RICOZ_ACCESS_TOKEN:?Set tenant admin JWT}"
curl -fsS -H "Authorization: Bearer ${RICOZ_ACCESS_TOKEN}" "${RICOZ_API_BASE}/api/v1/ai-platform/governance/usage"
printf '\nAI usage read succeeded. Run the tenant, approval and migration checks in PHASE5_AI_INTELLIGENCE_UPGRADE.md.\n'
