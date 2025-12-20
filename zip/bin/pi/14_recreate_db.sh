#!/usr/bin/env bash
set -euo pipefail

# Usage: 14_recreate_db.sh [dbname]
DB=${1:-law_db}
CHARSET=${2:-utf8mb4}
COLLATE=${3:-utf8mb4_general_ci}

echo "Create database $DB if not exists"
mysql -h 127.0.0.1 -u root -proot -e "CREATE DATABASE IF NOT EXISTS \\`${DB}\\` CHARACTER SET ${CHARSET} COLLATE ${COLLATE};"

echo "Done."
mysql -h 127.0.0.1 -u root -proot -e "SHOW DATABASES LIKE '${DB}';"
