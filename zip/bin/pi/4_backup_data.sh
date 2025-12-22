#!/usr/bin/env bash
set -euo pipefail

# backup_data.sh
# Archive le dossier data/ et le place dans /home/pi/backups avec timestamp.
# Usage: ./backup_data.sh [--src /path/to/data] [--dest /home/pi/backups]

SRC="/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/data"
DEST="/home/pi/backups"

while [[ $# -gt 0 ]]; do
  case $1 in
    --src) SRC="$2"; shift 2;;
    --dest) DEST="$2"; shift 2;;
    -h|--help) echo "Usage: $0 [--src PATH] [--dest PATH]"; exit 0;;
    *) echo "Unknown arg: $1"; exit 1;;
  esac
done

if [ ! -d "$SRC" ]; then
  echo "Source $SRC not found" >&2; exit 2
fi

mkdir -p "$DEST"
TS=$(date -u +"%Y%m%dT%H%M%SZ")
OUT="$DEST/law-data-backup-${TS}.tar.gz"

echo "Creating backup $OUT from $SRC"
sudo tar -C "$(dirname "$SRC")" -czf "$OUT" "$(basename "$SRC")"
sudo chown pi:pi "$OUT"
echo "Backup created: $OUT"