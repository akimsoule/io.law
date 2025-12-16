#!/bin/bash

###############################################################################
# Tests Fonctionnels - io.law (CLI Sans Spring)
###############################################################################

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🧪 Tests fonctionnels io.law CLI"
echo "=================================="

# Build du JAR si nécessaire
echo "📦 Vérification du JAR..."
cd "$PROJECT_ROOT"

JAR="law-app/target/law-app-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "⚠️ JAR non trouvé, compilation..."
    mvn clean package -DskipTests -q
fi

# === 1. FETCH JOBS ===
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "📋 SECTION 1: FETCH JOBS"
echo "═══════════════════════════════════════════════════════════"

echo ""
echo "📄 Test 1.1: fetchCurrent (scan complet type=loi)"
java -jar $JAR --job=fetchCurrent --type=loi || exit 1

echo ""
echo "📄 Test 1.2: fetchPrevious (max 5 items)"
java -jar $JAR --job=fetchPrevious --type=loi --maxItems=5 || exit 1

# === 2. DOWNLOAD JOB ===
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "📋 SECTION 2: DOWNLOAD JOB"
echo "═══════════════════════════════════════════════════════════"

echo ""
echo "📥 Test 2.1: download (max 5 documents)"
java -jar $JAR --job=download --type=loi --maxDocuments=5 || exit 1

# === 3. LAW-TOJSON JOBS ===
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "📋 SECTION 3: LAW-TOJSON JOBS"
echo "═══════════════════════════════════════════════════════════"

echo ""
echo "🔄 Test 3.1: ocr (extraction OCR pour type loi)"
java -jar $JAR --job=ocr --type=loi || exit 1

echo ""
echo "📄 Test 3.2: extract (parsing articles pour type loi)"
java -jar $JAR --job=extract --type=loi || exit 1

echo ""
echo "✅ Test 3.3: validate (quality assurance pour type loi)"
java -jar $JAR --job=validate --type=loi || exit 1

echo ""
echo "🤖 Test 3.4: ia (AI enhancement pour type loi)"
java -jar $JAR --job=ia --type=loi || exit 1

# === 4. FULL PIPELINE (DOCUMENT UNIQUE) ===
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "📋 SECTION 4: FULL PIPELINE (DOCUMENT CIBLÉ)"
echo "═══════════════════════════════════════════════════════════"

echo ""
echo "🚀 Test 4.1: fullJob (pipeline complet pour loi-2024-15)"
java -jar $JAR --job=fullJob --doc=loi-2024-15 || exit 1

echo ""
echo "❌ Test 4.2: fullJob sans --doc (doit échouer)"
if java -jar $JAR --job=fullJob 2>/dev/null; then
    echo "ERREUR: fullJob devrait échouer sans --doc"
    exit 1
else
    echo "✅ Échec attendu confirmé (--doc obligatoire)"
fi

# === 5. ORCHESTRATION COMPLÈTE ===
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "📋 SECTION 5: ORCHESTRATION COMPLÈTE"
echo "═══════════════════════════════════════════════════════════"

echo ""
echo "🚀 Test 5.1: orchestrate (pipeline complet pour type loi)"
echo "⚠️ Test désactivé (trop long pour CI)"
# gtimeout 600 java -jar $JAR --job=orchestrate --type=loi || exit 1

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "🎉 Tous les tests passés avec succès !"
echo "═══════════════════════════════════════════════════════════"
