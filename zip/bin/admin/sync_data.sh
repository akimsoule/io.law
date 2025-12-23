#!/bin/bash

# Configuration par défaut
REMOTE="pi@192.168.0.52"
LOCAL="./data/"
REMOTE_PATH="/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/data/"
MODE="push"

# Détection du mode pull n'importe où dans les arguments
for arg in "$@"; do
    if [ "$arg" == "pull" ]; then MODE="pull"; fi
done

# Récupération des arguments s'ils existent et ne sont pas "pull"
if [[ -n "$1" && "$1" != "pull" ]]; then REMOTE=$1; fi
if [[ -n "$2" && "$2" != "pull" ]]; then LOCAL=$2; fi
if [[ -n "$3" && "$3" != "pull" ]]; then REMOTE_PATH=$3; fi

# Nettoyage des chemins (slash de fin)
LOCAL="${LOCAL%/}/"
REMOTE_PATH="${REMOTE_PATH%/}/"

mkdir -p "$LOCAL"

if [ "$MODE" == "pull" ]; then
  echo "📥 MODE PULL : Récupération de $REMOTE vers $LOCAL..."
  rsync -avz --ignore-existing "$REMOTE:$REMOTE_PATH" "$LOCAL"
else
  echo "📤 MODE PUSH : Envoi de $LOCAL vers $REMOTE..."
  rsync -avz --ignore-existing "$LOCAL" "$REMOTE:$REMOTE_PATH"
  ssh "$REMOTE" "chown -R pi:pi $REMOTE_PATH 2>/dev/null || true"
fi

echo "Sync terminée."
