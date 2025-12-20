#!/usr/bin/env bash
set -euo pipefail

# Usage: 13_purge_batch_tables.sh [--yes]
# WARNING: will DROP all BATCH_* tables in the specified DB (default: law_db)

DB=${BATCH_DB:-law_db}
CONFIRM=""

for arg in "$@"; do
  case "$arg" in
    --yes) CONFIRM=1 ;;
    *) echo "Unknown option: $arg"; exit 2 ;;
  esac
done

if [ -z "$CONFIRM" ]; then
  echo "This will DROP all tables matching BATCH_% in database $DB"
  echo "Rerun with --yes to proceed."
  exit 1
fi

SQL="SET FOREIGN_KEY_CHECKS=0; SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema='${DB}' AND TABLE_NAME LIKE 'BATCH_%';"
TABLES=$(mysql -h 127.0.0.1 -u root -proot -N -s -e "$SQL") || true

if [ -z "$TABLES" ]; then
  echo "No BATCH_* tables found in $DB"
  exit 0
fi

echo "Dropping the following tables in $DB:"
echo "$TABLES"

# Construct DROP list
DROP_CMD="SET FOREIGN_KEY_CHECKS=0;"
for t in $TABLES; do
  DROP_CMD+=" DROP TABLE IF EXISTS \\`$DB\\`.\\`$t\\`;"
done
DROP_CMD+=" SET FOREIGN_KEY_CHECKS=1;"

mysql -h 127.0.0.1 -u root -proot -e "$DROP_CMD"

echo "Done."