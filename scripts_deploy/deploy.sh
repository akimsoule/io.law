#!/bin/bash
# deploy.sh - Déploiement sur Hostinger
set -e
cd "$(dirname "$0")"

echo "🚀 Déploiement io.law..."

# Créer .env si inexistant
if [[ ! -f .env ]]; then
    echo "📝 Création .env depuis .env.example..."
    cp .env.example .env
    echo "✅ .env créé avec valeurs par défaut"
fi

# Créer répertoires nécessaires
mkdir -p ../logs ../data

# Build et démarrage
echo "🐋 Build et démarrage des conteneurs..."
docker compose up -d --build

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Déploiement terminé !"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 Commandes utiles:"
echo "   docker compose logs -f app      # Suivre logs"
echo "   docker compose ps               # État conteneurs"
echo "   docker compose down             # Arrêter"
echo "   docker compose restart app      # Redémarrer app"
echo ""
