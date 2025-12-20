#!/usr/bin/env bash
set -euo pipefail

# Usage: 11_sync_from_remote.sh <remote_user@host> <remote_path> [--ignore-existing] [--dry-run]
# Example: ./11_sync_from_remote.sh akimsoule@192.168.0.37:/path/to/data /home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/data --ignore-existing

REMOTE=${1:-}
DEST=${2:-/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/data/}
IGNORE=""
DRYRUN=""

for arg in "${@:3}"; do
  case "$arg" in
    --ignore-existing) IGNORE="--ignore-existing" ;;
    --dry-run) DRYRUN="--dry-run" ;;
    *) echo "Unknown option: $arg"; exit 2 ;;
  esac
done

if [ -z "$REMOTE" ]; then
  echo "Usage: $0 <remote_user@host:/path> [dest] [--ignore-existing] [--dry-run]"
  exit 2
fi

echo "Syncing from $REMOTE to $DEST"
rsync -avzP $DRYRUN $IGNORE "$REMOTE" "$DEST"

echo "Fixing ownership..."
sudo chown -R pi:pi "$DEST" || true

echo "Done."