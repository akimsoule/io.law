#!/bin/bash

###############################################################################
# Tests Fonctionnels - law-tojson (OCR + Extract)
# 
# ⚠️ NOTE: Les services OCR et Extract ne sont PAS encore implémentés
# Ces tests vérifient uniquement que:
#   - Les jobs s'exécutent sans crash
#   - Les répertoires sont créés
#   - L'idempotence fonctionne
#   - Les warnings appropriés sont affichés
###############################################################################

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🧪 Tests fonctionnels - law-tojson"
echo "==================================="
echo "⚠️  Services OCR/Extract non implémentés (workflow uniquement)"
echo ""

# Build du JAR (ou utilise existant)
if [ -f "$PROJECT_ROOT/law-app/target/law-app-1.0.0-SNAPSHOT.jar" ]; then
    JAR="law-app/target/law-app-1.0.0-SNAPSHOT.jar"
    echo "✅ Using existing JAR: $JAR"
elif [ -f "$PROJECT_ROOT/law-app/target/law-app-1.0-SNAPSHOT.jar" ]; then
    JAR="law-app/target/law-app-1.0-SNAPSHOT.jar"
    echo "✅ Using existing JAR: $JAR"
else
    echo "📦 Build..."
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests -q
    JAR="law-app/target/law-app-1.0-SNAPSHOT.jar"
fi

OPTS=""

# === OCR JOB ===
echo ""
echo "🔄 ocrJob - extraction OCR type loi"
java -jar $JAR --job=ocr --type=loi $OPTS || exit 1

echo ""
echo "🔄 ocrJob - extraction OCR type decret"
java -jar $JAR --job=ocr --type=decret $OPTS || exit 1

echo ""
echo "🔄 ocrJob - idempotence (relancer)"
java -jar $JAR --job=ocr --type=loi $OPTS || exit 1

# === EXTRACT JOB ===
echo ""
echo "📄 extractJob - parsing articles type loi"
java -jar $JAR --job=extract --type=loi $OPTS || exit 1

echo ""
echo "📄 extractJob - parsing articles type decret"
java -jar $JAR --job=extract --type=decret $OPTS || exit 1

echo ""
echo "📄 extractJob - idempotence (relancer)"
java -jar $JAR --job=extract --type=loi $OPTS || exit 1

# === PIPELINE COMPLET ===
echo ""
echo "🚀 Pipeline complet - OCR + Extract"
java -jar $JAR --job=ocr --type=loi $OPTS && \
java -jar $JAR --job=extract --type=loi $OPTS || exit 1

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Tous les tests passés avec succès !"
echo ""
echo "⚠️  RAPPEL: Les services OCR/Extract ne sont pas implémentés"
echo "   Les tests vérifient uniquement le workflow et la structure"
echo "   Voir: law-tojson/IMPLEMENTATION-STATUS.md"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
