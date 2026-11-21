#!/usr/bin/env bash
set -euo pipefail
: "${RICOZ_MCP_KEY:?Set a tenant-scoped MCP credential}"
API="${RICOZ_MCP_API:-http://localhost:8080}"
curl --fail-with-body --silent --show-error "$API/mcp" \
  -X POST -H "Authorization: Bearer $RICOZ_MCP_KEY" \
  -H 'Content-Type: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' -H 'Mcp-Method: tools/list' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}'
