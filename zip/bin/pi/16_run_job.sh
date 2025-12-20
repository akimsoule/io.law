#!/usr/bin/env bash
set -euo pipefail

# Usage: 16_run_job.sh --job=fetchCurrentJob [--type=loi|decret] [--documentId=...] [--skip-fetch-daily=true]
# This wraps the orchestrator script invocation for manual runs.

ARGS=()
for arg in "$@"; do
  ARGS+=("$arg")
done

SCRIPT="/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/bin/9_orchestrate.sh"
if [ ! -x "$SCRIPT" ]; then
  echo "Orchestrator script not found or not executable: $SCRIPT" >&2
  exit 2
fi

echo "Running orchestrator: $SCRIPT ${ARGS[*]}"
bash "$SCRIPT" "${ARGS[@]}"
