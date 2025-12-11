#!/bin/bash
# Synchronise les données depuis le serveur Hostinger vers localhost

SERVER_USER="root"
SERVER_HOST=""  # À remplir: IP du serveur Hostinger
SERVER_PATH="~/io.law"  # Chemin du projet sur le serveur (configuré par setup-server.sh)
LOCAL_PATH="/Volumes/FOLDER/dev/projects/io.law"

# Vérification
if [ -z "$SERVER_HOST" ]; then
    echo "❌ Éditez le script et renseignez SERVER_HOST (IP du serveur)"
    exit 1
fi

echo "🔄 Sync serveur → localhost"
echo ""

# MySQL
echo "📊 MySQL..."
ssh ${SERVER_USER}@${SERVER_HOST} "cd ${SERVER_PATH}/scripts_deploy && docker exec law-mysql mysqldump -u root -plaw_password law_db" | \
  docker exec -i law-mysql mysql -u root -plaw_password law_db
echo "✅ Base synchronisée"

# Fichiers
echo "📁 Fichiers..."
rsync -avz --progress ${SERVER_USER}@${SERVER_HOST}:${SERVER_PATH}/data/ "$LOCAL_PATH/data/"
echo "✅ Fichiers synchronisés"

echo ""
echo "✅ Terminé"
