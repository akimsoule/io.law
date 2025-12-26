#!/usr/bin/env bash
set -euo pipefail

# drop_db_local.sh — supprime une base MySQL locale via le client mysql
# Usage: ./drop_db_local.sh <db_name> [root_pwd]

DB_NAME="${1:-law_db}"
MYSQL_PWD="${2:-}"

if [ -z "$DB_NAME" ]; then
  echo "Usage: $0 <db_name> [root_pwd]"
  exit 1
fi

if [ -n "$MYSQL_PWD" ]; then
  mysql -u root -p"$MYSQL_PWD" -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;"
else
  mysql -u root -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;"
fi

echo "Database $DB_NAME dropped on local MySQL"
