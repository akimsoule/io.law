#!/usr/bin/env bash
set -euo pipefail

# Usage: 17_backup_data.sh [dest-dir]
SRC=${1:-/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/data}
DEST_DIR=${2:-/home/pi/backups}
mkdir -p "$DEST_DIR"
TS=$(date +%Y%m%d_%H%M%S)
ARCHIVE="$DEST_DIR/law-data-backup-$TS.tar.gz"

echo "Creating backup $ARCHIVE from $SRC"
sudo tar -czf "$ARCHIVE" -C "$(dirname "$SRC")" "$(basename "$SRC")"

sudo chown pi:pi "$ARCHIVE" || true

echo "Backup created: $ARCHIVE"