#!/bin/bash
# Very simple procedural runner (no arguments)
# Placed in zip/bin/pi to run the default pipeline once.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAR="$(ls -1 "$PROJECT_ROOT"/lib/jar/law-app-*.jar 2>/dev/null | tail -n1 || true)"
CONFIG="$PROJECT_ROOT/properties/application-raspi.yml"
LOG_FILE="$PROJECT_ROOT/logs/procedural.log"

# Ensure strict failure handling
set -euo pipefail
trap 'echo "ERROR: $(basename "$0") failed at line $LINENO" >&2' ERR

# Defaults
TYPE="loi"
JOBS="fetchCurrentJob,fetchPreviousJob,downloadJob"

mkdir -p "$PROJECT_ROOT/logs"
# Log header
echo "=== $(basename "$0") START $(date) (type=$TYPE) ===" >> "$LOG_FILE"

cd "$PROJECT_ROOT"

# Fail if jar missing (do not build automatically)
if [[ -z "$JAR" ]]; then
  echo "ERROR: Jar not found in $PROJECT_ROOT/lib/jar. Aborting." | tee -a "$LOG_FILE" >&2
  exit 2
fi

# Run the app with specific jobs (fetch current, previous, download)
java -jar "$JAR" --spring.config.location="file:$CONFIG" --spring.profiles.active=procedural --jobs="$JOBS" --type="$TYPE" 2>&1 | tee -a "$LOG_FILE"
