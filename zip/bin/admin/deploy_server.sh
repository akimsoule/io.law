#!/bin/bash
# deploy_server.sh

REMOTE=${1:-pi@192.168.0.52}
ZIP=${2:-/Volumes/FOLDER/dev/projects/io.law/law-app/target/law-app-2.0.0-SNAPSHOT-distribution.zip}
DEST=${3:-/home/pi/law-app}

# 1. Vérification locale
if [ ! -f "$ZIP" ]; then echo "ZIP introuvable : $ZIP"; exit 1; fi

# 2. Transfert
echo "Upload vers $REMOTE..."
scp "$ZIP" "$REMOTE:/tmp/app.zip"

# 3. Extraction et droits
echo "Extraction dans $DEST..."
ssh "$REMOTE" << EOF
  mkdir -p "$DEST"
  unzip -o /tmp/app.zip -d "$DEST"
  chmod +x "$DEST"/**/*.sh 2>/dev/null || true
  rm /tmp/app.zip
EOF

echo "Déploiement terminé."
