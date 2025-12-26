#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# Start mysql service locally
echo "Starting mysql (local)..."
# try to use brew service if available, otherwise assume mysql is running
if command -v brew >/dev/null 2>&1; then
  echo "Starting mysql via brew..."
  brew services start mysql || true
fi

# Wait for mysql to be reachable
echo "Waiting for mysql to be reachable (timeout 120s)..."
for i in {1..60}; do
  if mysqladmin ping -uroot >/dev/null 2>&1; then
    echo "mysql is reachable"
    break
  fi
  echo "mysql not reachable yet (attempt $i)..."
  sleep 2
done

if ! mysqladmin ping -uroot >/dev/null 2>&1; then
  echo "ERROR: mysql did not become reachable in time" >&2
  exit 1
fi

# Start only fetch-current (single instance) to let Hibernate perform any DDL safely
echo "Starting fetch-current (local)..."
# start in background using the orchestrator script
nohup ./zip/bin/orchestrator/1_fetchCurrentOrchestrator.sh loi >/dev/null 2>&1 &

# Wait until fetch-current logs include a DB init success marker
echo "Waiting for fetch-current to complete DB initialization (timeout 10min)..."
for i in {1..300}; do
  if grep -q "LawDocumentService initialized" zip/logs/fetchcurrent.log 2>/dev/null; then
    echo "fetch-current initialization marker found"
    break
  fi
  echo "waiting for fetch-current init (attempt $i)..."
  sleep 2
done

if ! grep -q "LawDocumentService initialized" zip/logs/fetchcurrent.log 2>/dev/null; then
  echo "WARNING: fetch-current did not show expected init marker; review logs" >&2
fi

# Start remaining orchestrators
echo "Starting remaining orchestrators..."
nohup ./zip/bin/orchestrator/2_fetchPreviousOrchestrator.sh loi >/dev/null 2>&1 &
nohup ./zip/bin/orchestrator/3_downloadOrchestrator.sh loi >/dev/null 2>&1 &
nohup ./zip/bin/orchestrator/4_ocrOrchestrator.sh loi >/dev/null 2>&1 &
nohup ./zip/bin/orchestrator/5_ocrJsonOrchestrator.sh loi >/dev/null 2>&1 &
nohup ./zip/bin/orchestrator/6_jsonConversionOrchestrator.sh loi >/dev/null 2>&1 &
nohup ./zip/bin/orchestrator/7_consolidateOrchestrator.sh loi >/dev/null 2>&1 &

echo "All orchestrators started (or already running). Use 'ps' or check 'zip/logs/' to inspect."