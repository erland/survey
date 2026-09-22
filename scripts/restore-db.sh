#!/usr/bin/env sh
set -eu
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <backup.dump>" >&2
  exit 2
fi
COMPOSE_FILE=${COMPOSE_FILE:-compose.production.yaml}
backup=$1
[ -f "$backup" ] || { echo "Backup not found: $backup" >&2; exit 2; }
cat "$backup" | docker compose -f "$COMPOSE_FILE" exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges'
echo "Restore completed from $backup"
