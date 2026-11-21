#!/usr/bin/env bash
set -euo pipefail
: "${PGHOST:?PGHOST required}" "${PGPORT:=5432}" "${PGDATABASE:?PGDATABASE required}" "${PGUSER:?PGUSER required}" "${BACKUP_DIR:=./backups}"
mkdir -p "$BACKUP_DIR"
ts="$(date -u +%Y%m%dT%H%M%SZ)"
pg_dump --format=custom --file="$BACKUP_DIR/ricozknow-$ts.dump" "$PGDATABASE"
find "$BACKUP_DIR" -type f -name '*.dump' -mtime +30 -delete
echo "Created $BACKUP_DIR/ricozknow-$ts.dump"
