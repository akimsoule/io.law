#!/bin/bash
# Usage: ./7_consolidateJob.sh [type]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/lib/jar/law-app-2.0.0-SNAPSHOT.jar"
CONFIG="$PROJECT_ROOT/properties/application.yml"
LOG_FILE="$PROJECT_ROOT/logs/consolidate.log"

TYPE="${1:-loi}"

mkdir -p "$PROJECT_ROOT/logs"
cd "$PROJECT_ROOT"
java -jar "$JAR" --spring.config.location="file:$CONFIG" --job=consolidateJob --type="$TYPE" 2>&1 | tee "$LOG_FILE"
