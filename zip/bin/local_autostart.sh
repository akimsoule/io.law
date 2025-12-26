#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# Utilise flock pour lancer des tâches en 'fire and forget' avec verrouillage
# Usage simple: ./zip/bin/local_autostart.sh (démarre fetch-current puis les autres)

BASE_DIR="$ROOT/zip/bin/orchestrator"
mkdir -p "$ROOT/zip/logs"

lancer_tache() {
  local script_path="$1"
  local lock_name="$2"
  # Exécute la commande via flock en arrière-plan; si verrou pris -n, flock sort discrètement
  flock -n "/tmp/${lock_name}.lock" -c "bash '$script_path' loi" &
}

# Démarrage ordonné : fetch-current d'abord
lancer_tache "$BASE_DIR/1_fetchCurrentOrchestrator.sh" "lock_fetch_current"
# petit délai pour laisser le fetch-current démarrer
sleep 2

# Lancer les autres en 'fire and forget'
lancer_tache "$BASE_DIR/2_fetchPreviousOrchestrator.sh" "lock_fetch_previous"
lancer_tache "$BASE_DIR/3_downloadOrchestrator.sh" "lock_download"
lancer_tache "$BASE_DIR/4_ocrOrchestrator.sh" "lock_ocr"
lancer_tache "$BASE_DIR/5_ocrJsonOrchestrator.sh" "lock_ocr_json"
lancer_tache "$BASE_DIR/6_jsonConversionOrchestrator.sh" "lock_json_conversion"
lancer_tache "$BASE_DIR/7_consolidateOrchestrator.sh" "lock_consolidate"

# Le script se termine immédiatement; les tâches continuent en arrière-plan
exit 0
