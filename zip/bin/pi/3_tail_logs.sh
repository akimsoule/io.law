#!/usr/bin/env bash
set -euo pipefail

# tail_logs.sh
# Tail des logs de l'orchestrator (ou d'un fichier spécifié)
# Usage: ./tail_logs.sh [--file /path/to/log] [--lines 200] [-f]

LOG_FILE="/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/logs/orchestrate.log"
LINES=200
FOLLOW=0

while [[ $# -gt 0 ]]; do
  case $1 in
    --file) LOG_FILE="$2"; shift 2;;
    --lines) LINES="$2"; shift 2;;
    -f) FOLLOW=1; shift 1;;
    -h|--help) echo "Usage: $0 [--file PATH] [--lines N] [-f]"; exit 0;;
    *) echo "Unknown arg: $1"; exit 1;;
  esac
done

if [ ! -f "$LOG_FILE" ]; then
  echo "Log file not found: $LOG_FILE" >&2; exit 2
fi

echo "Showing last $LINES lines of $LOG_FILE"
if [ $FOLLOW -eq 1 ]; then
  tail -n "$LINES" -f "$LOG_FILE"
else
  tail -n "$LINES" "$LOG_FILE"
fi