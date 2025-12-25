#!/bin/bash
# Usage: ./9_orchestrate.sh [type] [skip-fetch-daily]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/lib/jar/law-app-2.0.0-SNAPSHOT.jar"
CONFIG="$PROJECT_ROOT/properties/application-raspi.yml"
LOG_FILE="$PROJECT_ROOT/logs/orchestrate.log"

# Source configuration JVM optimisée
source "$PROJECT_ROOT/properties/jvm.conf"

TYPE="${1:-decret}"
SKIP_FETCH_DAILY="${2:-true}"

mkdir -p "$PROJECT_ROOT/logs"
STATUS_DIR="$PROJECT_ROOT/logs/status"
mkdir -p "$STATUS_DIR"
# Ensure a log header exists so we always have content
echo "=== ORCHESTRATION START $(date) (type=$TYPE) ===" >> "$LOG_FILE"
cd "$PROJECT_ROOT"

ORCH_DIR="$SCRIPT_DIR/orchestrator"
if [ ! -d "$ORCH_DIR" ]; then
  echo "ERROR: orchestrator directory not found: $ORCH_DIR" | tee -a "$LOG_FILE"
  exit 1
fi

# Execute each script found in $ORCH_DIR in filename order (numeric prefixes preserved)
for script in "$ORCH_DIR"/*.sh; do
  [ -f "$script" ] || continue
  echo "========================================" | tee -a "$LOG_FILE"
  echo "Executing: $(basename "$script")" | tee -a "$LOG_FILE"
  chmod +x "$script"
  # Run the script with the TYPE argument and append output to the central log
  "$script" "$TYPE" 2>&1 | tee -a "$LOG_FILE"
  rc=${PIPESTATUS[0]:-0}
  if [ "$rc" -ne 0 ]; then
    echo "WARNING: script $(basename "$script") exited with code $rc" | tee -a "$LOG_FILE"
    echo "$(date) $(basename "$script") FAILED $rc" > "$STATUS_DIR/$(basename "$script").FAILED"
  else
    echo "Completed: $(basename "$script")" | tee -a "$LOG_FILE"
    echo "$(date) $(basename "$script") OK" > "$STATUS_DIR/$(basename "$script").OK"
  fi
  echo "" | tee -a "$LOG_FILE"
done

echo "All orchestrator scripts processed." | tee -a "$LOG_FILE"
