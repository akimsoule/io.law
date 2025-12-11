#!/bin/bash
# Orchestration continue du pipeline complet
# Arrêt: Ctrl+C

set -e

# Configuration pour Docker ou local
if [ -f "/app/app.jar" ]; then
    # Mode Docker
    JAR_PATH="/app/app.jar"
    LOG_DIR="/app/logs"
else
    # Mode local
    JAR_PATH="law-app/target/law-app-1.0-SNAPSHOT.jar"
    LOG_DIR="logs"
fi

LOG_FILE="${LOG_DIR}/orchestrator-$(date +%Y%m%d-%H%M%S).log"

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
echo "📋 Pipeline: fetchCurrent → fetchPrevious → download → extract → consolidate → fix"
echo "🔄 Mode: Continu (arrêt: Ctrl+C)"
echo "📝 Logs: $LOG_FILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Lancer orchestration
java $JAVA_OPTS -jar "$JAR_PATH" \
    --job=orchestrate \
    --spring.main.web-application-type=none \
    2>&1 | tee "$LOG_FILE"

# Fin
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🏁 Orchestration arrêtée"
echo "📝 Logs sauvegardés: $LOG_FILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
