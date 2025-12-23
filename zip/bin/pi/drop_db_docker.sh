#!/usr/bin/env bash
set -euo pipefail

# drop_db_docker.sh ultra-simple
# Usage: ./2_drop_db_docker.sh <container> <db_name> [root_pwd]

CONTAINER="${1:-mysql}"
DB_NAME="${2:-law_db}"
MYSQL_PWD="${3:-}"

if [ -z "$CONTAINER" ] || [ -z "$DB_NAME" ]; then
  echo "Usage: $0 <container> <db_name> [root_pwd]"
  exit 1
fi

if [ -n "$MYSQL_PWD" ]; then
  docker exec -i "$CONTAINER" mysql -h 127.0.0.1 -u root -p"$MYSQL_PWD" -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;"
else
  docker exec -i "$CONTAINER" mysql -h 127.0.0.1 -u root -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;"
fi

echo "Database $DB_NAME dropped in container $CONTAINER"