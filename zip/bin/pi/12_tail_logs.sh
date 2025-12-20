#!/usr/bin/env bash
set -euo pipefail

# Usage: 12_tail_logs.sh [lines] [logfile]
# Default: lines=200, logfile=logs/orchestrate.log

LINES=${1:-200}
LOGFILE=${2:-/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/logs/orchestrate.log}

if [ ! -f "$LOGFILE" ]; then
  echo "Logfile not found: $LOGFILE"
  exit 1
fi

echo "Tailing $LOGFILE (last $LINES lines)"

# follow by default if no -n provided
tail -n "$LINES" -f "$LOGFILE"