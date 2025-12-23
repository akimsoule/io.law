#!/bin/bash
# Usage: ./9_orchestrate.sh [type] [skip-fetch-daily]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/lib/jar/law-app-2.0.0-SNAPSHOT.jar"
CONFIG="$PROJECT_ROOT/properties/application-raspi.yml"
LOG_FILE="$PROJECT_ROOT/logs/orchestrate.log"

# Source configuration JVM optimisée
source "$PROJECT_ROOT/properties/jvm.conf"

TYPE="${1:-loi}"
SKIP_FETCH_DAILY="${2:-true}"

mkdir -p "$PROJECT_ROOT/logs"
cd "$PROJECT_ROOT"
java -jar "$JAR" --spring.config.location="file:$CONFIG" --job=orchestrate --type="$TYPE" --skip-fetch-daily="$SKIP_FETCH_DAILY" 2>&1 | tee "$LOG_FILE"
