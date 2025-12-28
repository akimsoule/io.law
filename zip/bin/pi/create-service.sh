#!/bin/bash

# Définition des chemins selon vos informations
SERVICE_NAME="procedural-law"
# Chemin absolu vers votre script dans le dossier zip
SCRIPT_PATH="/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/bin/run_procedural-law.sh"
WORKING_DIR="/home/pi/law-app/law-orchestrator-2.0.0-SNAPSHOT/bin"

echo "--- Installation du service $SERVICE_NAME ---"

# 1. Vérification de l'existence du fichier
if [ ! -f "$SCRIPT_PATH" ]; then
    echo "ERREUR : Le script est introuvable au chemin : $SCRIPT_PATH"
    exit 1
fi

# 2. Rendre le script exécutable
chmod +x "$SCRIPT_PATH"

# 3. Création du fichier de service systemd
sudo bash -c "cat <<EOF > /etc/systemd/system/$SERVICE_NAME.service
[Unit]
Description=Service Procedural Law Orchestrator
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=$WORKING_DIR
ExecStart=/bin/bash $SCRIPT_PATH
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
EOF"

# 4. Recharger systemd et activer le service
sudo systemctl daemon-reload
sudo systemctl enable $SERVICE_NAME

echo "Succès ! Le service est prêt."
echo "Utilisez Cockpit (port 9090) pour le démarrer et voir les logs."
