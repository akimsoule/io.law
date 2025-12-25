#!/bin/bash
# Usage: ./orchestrator/1_fetchCurrentOrchestrator.sh [type]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAR="$PROJECT_ROOT/lib/jar/law-app-2.0.0-SNAPSHOT.jar"
CONFIG="$PROJECT_ROOT/properties/application-raspi.yml"
LOG_FILE="$PROJECT_ROOT/logs/fetchcurrent.log"

# Source configuration JVM optimisée
source "$PROJECT_ROOT/properties/jvm.conf"

# Ensure strict failure handling so errors are visible
set -euo pipefail
trap 'echo "ERROR: $(basename "$0") failed at line $LINENO" >&2; mkdir -p "$PROJECT_ROOT/logs/status"; echo "$(date) $(basename "$0") FAILED $LINENO" > "$PROJECT_ROOT/logs/status/$(basename "$0").FAILED"' ERR

TYPE="${1:-loi}"

mkdir -p "$PROJECT_ROOT/logs"
# Ensure log header exists for this script
echo "=== $(basename "$0") START $(date) (type=$TYPE) ===" >> "$LOG_FILE"
cd "$PROJECT_ROOT"
java -jar "$JAR" --spring.config.location="file:$CONFIG" --orchestrator=fetchCurrentOrchestrator --mode=once --type="$TYPE" 2>&1 | tee "$LOG_FILE"
# Write status file on success
mkdir -p "$PROJECT_ROOT/logs/status"
echo "$(date) $(basename "$0") OK" > "$PROJECT_ROOT/logs/status/$(basename "$0").OK"