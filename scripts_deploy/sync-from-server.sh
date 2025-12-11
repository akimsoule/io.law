#!/bin/bash
# Synchronise les données depuis le serveur vers localhost

SERVER_USER="root"
SERVER_HOST=""  # À remplir: IP du serveur
SERVER_PATH="~/io.law"  # Chemin du projet sur le serveur (configuré par setup-server.sh)
LOCAL_PATH="/Volumes/FOLDER/dev/projects/io.law"

# Vérification
if [ -z "$SERVER_HOST" ]; then
    echo "❌ Éditez le script et renseignez SERVER_HOST (IP du serveur)"
    exit 1
fi

echo "🔄 Sync serveur → localhost"
echo ""

# MySQL dump
echo "📊 Export MySQL..."
DUMP_FILE="law_db_$(date +%Y%m%d_%H%M%S).sql"
ssh ${SERVER_USER}@${SERVER_HOST} "mysqldump -u root -proot law_db > ${SERVER_PATH}/data/${DUMP_FILE}"
echo "✅ Dump créé sur le serveur: ${DUMP_FILE}"

# Fichiers + MySQL dump
echo "📁 Téléchargement complet..."
rsync -avz --progress ${SERVER_USER}@${SERVER_HOST}:${SERVER_PATH}/data/ "$LOCAL_PATH/data/"
echo "✅ Données synchronisées"

echo ""
echo "💾 Dump MySQL disponible: data/${DUMP_FILE}"
echo "✅ Terminé"
