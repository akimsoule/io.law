#!/bin/bash

###############################################################################
# Tests Fonctionnels - io.law (Batch CLI)
###############################################################################

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🧪 Tests fonctionnels"
echo "===================="

# Build du JAR
echo "📦 Build..."
cd "$PROJECT_ROOT"
mvn clean package -DskipTests -q

JAR="law-app/target/law-app-1.0-SNAPSHOT.jar"
OPTS="--spring.main.web-application-type=none"

# === FETCH CURRENT JOB ===
echo ""
echo "📄 fetchCurrentJob - scan complet (max 5 docs)"
java -jar $JAR --job=fetchCurrentJob --maxDocuments=5 $OPTS || exit 1  # ✅ PASSED

echo ""
echo "📄 fetchCurrentJob - ciblé (loi-2024-15)"
java -jar $JAR --job=fetchCurrentJob --doc=loi-2024-15 $OPTS || exit 1  # ✅ PASSED

echo ""
echo "📄 fetchCurrentJob - ciblé avec --force"
java -jar $JAR --job=fetchCurrentJob --doc=loi-2024-15 --force=true $OPTS || exit 1  # ✅ PASSED

# === FETCH PREVIOUS JOB ===
echo ""
echo "📄 fetchPreviousJob - scan années précédentes (max 5 docs)"
java -jar $JAR --job=fetchPreviousJob --maxDocuments=5 $OPTS || exit 1

echo ""
echo "📄 fetchPreviousJob - ciblé (loi-2020-10)"
java -jar $JAR --job=fetchPreviousJob --doc=loi-2020-10 $OPTS || exit 1  # ✅ PASSED

echo ""
echo "📄 fetchPreviousJob - ciblé avec --force"
java -jar $JAR --job=fetchPreviousJob --doc=loi-2020-10 --force=true $OPTS || exit 1  # ✅ PASSED

# === DOWNLOAD JOB ===
echo ""
echo "📥 downloadJob - tous documents FETCHED (max 5 docs)"
java -jar $JAR --job=downloadJob --maxDocuments=5 $OPTS || exit 1

echo ""
echo "📥 downloadJob - ciblé (loi-2024-15)"
java -jar $JAR --job=downloadJob --documentId=loi-2024-15 $OPTS || exit 1  # ✅ PASSED

echo ""
echo "📥 downloadJob - ciblé avec --force"
java -jar $JAR --job=downloadJob --documentId=loi-2024-15 --force=true $OPTS || exit 1  # ✅ PASSED

# === PDF TO JSON JOB ===
echo ""
echo "📄 pdfToJsonJob - tous documents DOWNLOADED (max 5 docs)"
java -jar $JAR --job=pdfToJsonJob --maxDocuments=5 $OPTS || exit 1

echo ""
echo "📄 pdfToJsonJob - ciblé (loi-2024-15)"
java -jar $JAR --job=pdfToJsonJob --documentId=loi-2024-15 $OPTS || exit 1

echo ""
echo "📄 pdfToJsonJob - ciblé avec --force (re-traite si confiance supérieure)"
java -jar $JAR --job=pdfToJsonJob --documentId=loi-2024-15 --force=true $OPTS || exit 1

echo ""
echo "📄 pdfToJsonJob - avec limite personnalisée (max 10 docs)"
java -jar $JAR --job=pdfToJsonJob --maxDocuments=10 $OPTS || exit 1

# === CONSOLIDATE JOB ===
echo ""
echo "💾 consolidateJob - tous documents EXTRACTED"
java -jar $JAR --job=consolidateJob $OPTS || exit 1

# === FULL JOB ===
echo ""
echo "🚀 fullJob - pipeline complet pour un document (loi-2024-15)"
java -jar $JAR --job=fullJob --doc=loi-2024-15 $OPTS || exit 1

echo ""
echo "🔄 fullJob - test avec --force (retraitement complet)"
java -jar $JAR --job=fullJob --doc=loi-2024-15 --force $OPTS || exit 1

echo ""
echo "❌ fullJob - test sans paramètre --doc (doit échouer)"
if java -jar $JAR --job=fullJob $OPTS 2>/dev/null; then
    echo "ERREUR: fullJob devrait échouer sans --doc"
    exit 1
else
    echo "✅ Échec attendu confirmé (--doc obligatoire)"
fi

echo ""
echo "🎉 Tous les tests passés avec succès !"