#!/bin/bash
# Orchestration continue du pipeline complet
# Arrêt: Ctrl+C
#
# Usage:
#   ./scripts/orchestrate.sh                 # Skip fetchCurrent (défaut)
#   ./scripts/orchestrate.sh --fetch-current # Exécuter fetchCurrent

set -e

# Configuration pour Docker ou local
if [ -f "/app/app.jar" ]; then
    # Mode Docker
    JAR_PATH="/app/app.jar"
    LOG_DIR="/app/logs"
else
    # Mode local
    JAR_PATH="law-app/target/law-app-1.0.0-SNAPSHOT.jar"
    LOG_DIR="logs"
fi

LOG_FILE="${LOG_DIR}/orchestrator.log"
# LOG_FILE="${LOG_DIR}/orchestrator-$(date +%Y%m%d-%H%M%S).log"

# Parser options
SKIP_FETCH_CURRENT="true"
if [ "$1" = "--fetch-current" ]; then
    SKIP_FETCH_CURRENT="false"
    echo "⚙️  Option: Exécuter fetchCurrent à chaque cycle"
fi

# Vérifications
if [ ! -f "$JAR_PATH" ]; then
    echo "❌ JAR non trouvé: $JAR_PATH"
    echo "   Compiler d'abord: mvn clean package -DskipTests"
    exit 1
fi

# Créer répertoire logs
mkdir -p "$LOG_DIR"

# Banner
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Démarrage Orchestration Continue"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📋 Pipeline: fetch → download → ocr → extract → validate → ia"
echo "🔄 Mode: Continu (arrêt: Ctrl+C)"
echo "⚙️  Skip fetchCurrent: $SKIP_FETCH_CURRENT"
echo "📝 Logs: $LOG_FILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Lancer orchestration
java $JAVA_OPTS -jar "$JAR_PATH" \
    --job=orchestrate \
    --type=loi \
    --skipFetchCurrent="$SKIP_FETCH_CURRENT" \
    2>&1 | tee "$LOG_FILE"

# Fin
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🏁 Orchestration arrêtée"
echo "📝 Logs sauvegardés: $LOG_FILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
