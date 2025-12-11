#!/bin/bash
# Affiche les derniers logs de l'orchestration pour suivre l'avancement

LINES=${1:-50}  # Nombre de lignes (défaut: 50)

echo "📊 Derniers logs orchestration (${LINES} lignes)"
echo ""

# Trouver le dernier fichier de log orchestrator
LATEST_LOG=$(docker exec law-app sh -c "ls -t /app/logs/orchestrator-*.log 2>/dev/null | head -1")

if [ -z "$LATEST_LOG" ]; then
    echo "❌ Aucun log d'orchestration trouvé"
    exit 1
fi

echo "📄 Fichier: $(basename $LATEST_LOG)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Afficher les dernières lignes avec coloration
docker exec law-app tail -n $LINES "$LATEST_LOG" | \
  sed -E 's/(✅)/\o033[0;32m\1\o033[0m/g' | \
  sed -E 's/(❌)/\o033[0;31m\1\o033[0m/g' | \
  sed -E 's/(⏭️|⏸️)/\o033[1;33m\1\o033[0m/g' | \
  sed -E 's/(🔄)/\o033[0;34m\1\o033[0m/g'

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "💡 Voir plus de lignes: ./check-logs.sh 100"
echo "📋 Tous les logs: docker exec law-app ls -lh /app/logs/"
