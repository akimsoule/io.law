#!/usr/bin/env bash
set -euo pipefail

# drop_db_docker.sh
# Supprime une base de données depuis un container Docker MySQL/MariaDB.
# Usage: ./drop_db_docker.sh --db law_db [--container-name mysql] [--mysql-root-pwd root]

DB_NAME="law_db"
CONTAINER=""
MYSQL_PWD=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --db) DB_NAME="$2"; shift 2;;
    --container-name) CONTAINER="$2"; shift 2;;
    --mysql-root-pwd) MYSQL_PWD="$2"; shift 2;;
    -h|--help) echo "Usage: $0 [--db DBNAME] [--container-name NAME] [--mysql-root-pwd PWD]"; exit 0;;
    *) echo "Unknown arg: $1"; exit 1;;
  esac
done

if [ -z "$CONTAINER" ]; then
  # try to detect a mysql/mariadb container
  CONTAINER=$(docker ps -q --filter "ancestor=mysql" | head -n1 || true)
  if [ -z "$CONTAINER" ]; then
    CONTAINER=$(docker ps -q --filter "ancestor=mariadb" | head -n1 || true)
  fi
fi

if [ -z "$CONTAINER" ]; then
  echo "Aucun conteneur MySQL/MariaDB détecté. Passez --container-name <name> ou démarrez le container." >&2
  exit 2
fi

echo "Will DROP database '$DB_NAME' in container $CONTAINER"
read -rp "Type 'yes' to confirm: " confirm
if [ "$confirm" != "yes" ]; then
  echo "Aborted"
  exit 0
fi

MYSQL_CMD="mysql -h 127.0.0.1 -u root"
if [ -n "$MYSQL_PWD" ]; then
  MYSQL_CMD+=" -p'$MYSQL_PWD'"
fi

# execute drop
docker exec -i "$CONTAINER" bash -lc "$MYSQL_CMD -e \"DROP DATABASE IF EXISTS \`$DB_NAME\` ;\""

echo "Database $DB_NAME dropped on container $CONTAINER"