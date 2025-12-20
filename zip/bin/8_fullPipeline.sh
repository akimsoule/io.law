#!/bin/bash
# Usage: ./8_fullPipeline.sh
# Lance le pipeline complet pour type=loi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

JAR="$PROJECT_ROOT/lib/jar/law-app-2.0.0-SNAPSHOT.jar"
CONFIG="$PROJECT_ROOT/properties/application-raspi.yml"
LOG_DIR="$PROJECT_ROOT/logs"
LOG_FILE="$LOG_DIR/fullpipeline-law.log"

# Source configuration JVM optimisée
source "$PROJECT_ROOT/properties/jvm.conf"

mkdir -p "$LOG_DIR"

echo "📋 Logs: $LOG_FILE"
cd "$PROJECT_ROOT"
java -jar "$JAR" --spring.config.location="file:$CONFIG" --pipeline=fullPipeline --type=loi 2>&1 | tee "$LOG_FILE"
