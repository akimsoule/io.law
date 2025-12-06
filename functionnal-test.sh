#!/bin/bash

###############################################################################
# Tests Fonctionnels - io.law (Batch CLI)
###############################################################################

set -e
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "🧪 Tests fonctionnels"
echo "===================="

# Build du JAR
echo "📦 Build..."
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
echo ""
echo "🎉 Tous les tests passés avec succès !"