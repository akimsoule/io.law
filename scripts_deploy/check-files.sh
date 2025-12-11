#!/bin/bash
# Script de consultation des fichiers dans le volume Docker

echo "📁 Consultation Fichiers io.law"
echo "================================"
echo ""

# Vérifier que le conteneur tourne
if ! docker ps | grep -q law-app; then
    echo "❌ Conteneur law-app non démarré"
    echo "💡 Lancez: docker compose up -d"
    exit 1
fi

echo "📊 1. STRUCTURE DU VOLUME /data"
echo "────────────────────────────────"
docker exec law-app sh -c "
    if [ -d /data ]; then
        du -sh /data/* 2>/dev/null | sort -h || echo 'Volume vide'
    else
        echo 'Répertoire /data non monté'
    fi
"

echo ""
echo "📄 2. FICHIERS PDFs"
echo "───────────────────"
PDF_COUNT=$(docker exec law-app sh -c "find /data/pdfs -type f -name '*.pdf' 2>/dev/null | wc -l" 2>/dev/null || echo "0")
echo "Total PDFs: $PDF_COUNT"
if [ "$PDF_COUNT" -gt 0 ]; then
    echo ""
    echo "Derniers PDFs (top 10):"
    docker exec law-app sh -c "find /data/pdfs -type f -name '*.pdf' -exec ls -lh {} \; 2>/dev/null | tail -10 | awk '{print \$9, \$5}'"
fi

echo ""
echo "📝 3. FICHIERS OCR (.txt)"
echo "─────────────────────────"
OCR_COUNT=$(docker exec law-app sh -c "find /data/ocr -type f -name '*.txt' 2>/dev/null | wc -l" 2>/dev/null || echo "0")
echo "Total OCR: $OCR_COUNT"
if [ "$OCR_COUNT" -gt 0 ]; then
    echo ""
    echo "Derniers OCR (top 10):"
    docker exec law-app sh -c "find /data/ocr -type f -name '*.txt' -exec ls -lh {} \; 2>/dev/null | tail -10 | awk '{print \$9, \$5}'"
fi

echo ""
echo "📋 4. FICHIERS JSON"
echo "───────────────────"
JSON_COUNT=$(docker exec law-app sh -c "find /data/articles -type f -name '*.json' 2>/dev/null | wc -l" 2>/dev/null || echo "0")
echo "Total JSON: $JSON_COUNT"
if [ "$JSON_COUNT" -gt 0 ]; then
    echo ""
    echo "Derniers JSON (top 10):"
    docker exec law-app sh -c "find /data/articles -type f -name '*.json' -exec ls -lh {} \; 2>/dev/null | tail -10 | awk '{print \$9, \$5}'"
fi

echo ""
echo "📊 5. LOGS ORCHESTRATION"
echo "────────────────────────"
LOG_COUNT=$(docker exec law-app sh -c "find /app/logs -type f -name '*.log' 2>/dev/null | wc -l" 2>/dev/null || echo "0")
echo "Total logs: $LOG_COUNT"
if [ "$LOG_COUNT" -gt 0 ]; then
    echo ""
    echo "Derniers logs:"
    docker exec law-app sh -c "ls -lht /app/logs/*.log 2>/dev/null | head -5 | awk '{print \$9, \$5}'"
fi

echo ""
echo "💾 6. ESPACE DISQUE"
echo "───────────────────"
docker exec law-app df -h /data 2>/dev/null | tail -1 | awk '{print "Utilisé: " $3 " / " $2 " (" $5 ")"}'

echo ""
echo "✨ Terminé !"
