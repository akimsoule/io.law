#!/bin/bash
# Script d'installation et déploiement io.law sur Ubuntu Server
# Usage: curl -sSL https://raw.githubusercontent.com/akimsoule/io.law/main/scripts_deploy/setup-server.sh | bash

set -e  # Arrêter en cas d'erreur

echo "🚀 Installation io.law sur Ubuntu Server"
echo "========================================="
echo ""

# Vérifier si root
if [ "$EUID" -ne 0 ]; then 
    echo "❌ Ce script doit être exécuté en tant que root"
    echo "💡 Utilisez: sudo bash setup-server.sh"
    exit 1
fi

# Variables de configuration
INSTALL_DIR="~/io.law"
DATA_DIR="~/io.law/data"
LOGS_DIR="~/io.law/logs"
MYSQL_ROOT_PASSWORD="root"
REPO_URL="https://github.com/akimsoule/io.law.git"

echo "📋 Configuration:"
echo "   - Installation: $INSTALL_DIR"
echo "   - Données: $DATA_DIR"
echo "   - Logs: $LOGS_DIR"
echo "   - MySQL Password: $MYSQL_ROOT_PASSWORD"
echo ""

# ============================================
# 1. MISES À JOUR SYSTÈME
# ============================================
echo "📦 1/8 Mise à jour du système..."
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq curl wget git build-essential


# ============================================
# 2. INSTALLATION JAVA 17
# ============================================
echo "☕ 2/8 Installation Java 17..."
if ! command -v java &> /dev/null; then
    apt-get install -y -qq openjdk-17-jdk openjdk-17-jre
    echo "✅ Java installé"
else
    echo "✅ Java déjà installé: $(java -version 2>&1 | head -n 1)"
fi

# ============================================
# 3. INSTALLATION MAVEN
# ============================================
echo "📦 3/8 Installation Maven..."
if ! command -v mvn &> /dev/null; then
    apt-get install -y -qq maven
    echo "✅ Maven installé"
else
    echo "✅ Maven déjà installé: $(mvn -version | head -n 1)"
fi

# ============================================
# 4. INSTALLATION MYSQL 8
# ============================================
echo "🗄️  4/8 Installation MySQL..."

if ! command -v mysql &> /dev/null; then
    # Installer MySQL
    apt-get install -y -qq mysql-server mysql-client
    
    # Démarrer MySQL
    systemctl start mysql
    systemctl enable mysql
    
    echo "✅ MySQL installé"
else
    echo "✅ MySQL déjà installé"
    systemctl start mysql 2>/dev/null || true
    systemctl enable mysql 2>/dev/null || true
fi

# Configurer MySQL (toujours exécuté pour garantir config)
mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '$MYSQL_ROOT_PASSWORD';" 2>/dev/null || true
mysql -e "CREATE DATABASE IF NOT EXISTS law_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || true
mysql -e "FLUSH PRIVILEGES;" 2>/dev/null || true

echo "✅ MySQL configuré"

# ============================================
# 5. INSTALLATION OLLAMA
# ============================================
echo "🤖 5/8 Installation Ollama..."

if ! command -v ollama &> /dev/null; then
    # Installer Ollama
    curl -fsSL https://ollama.com/install.sh | sh
    echo "✅ Ollama installé"
else
    echo "✅ Ollama déjà installé"
fi

# Démarrer service Ollama
systemctl start ollama 2>/dev/null || true
systemctl enable ollama 2>/dev/null || true

# Attendre démarrage
sleep 2

# Vérifier si modèle existe, sinon télécharger
if ! ollama list | grep -q "gemma3n"; then
    echo "📥 Téléchargement modèle gemma3n (en arrière-plan)..."
    nohup ollama pull gemma3n > /tmp/ollama-pull.log 2>&1 &
else
    echo "✅ Modèle gemma3n déjà présent"
fi

# ============================================
# 6. CLONER REPOSITORY ET BUILD
# ============================================
echo "📥 6/8 Clonage et build du projet..."

# Créer répertoires
mkdir -p "$INSTALL_DIR"
mkdir -p "$DATA_DIR"
mkdir -p "$LOGS_DIR"

# Cloner repository
if [ -d "$INSTALL_DIR/.git" ]; then
    echo "⚠️  Repository déjà cloné, mise à jour..."
    cd "$INSTALL_DIR"
    git pull origin main
else
    git clone "$REPO_URL" "$INSTALL_DIR"
    cd "$INSTALL_DIR"
fi

# Build Maven (skip tests pour aller vite)
echo "🔨 Build du projet (peut prendre 5-10 minutes)..."
mvn clean install -DskipTests -q

# Vérifier JAR généré
if [ ! -f "$INSTALL_DIR/law-app/target/law-app-1.0-SNAPSHOT.jar" ]; then
    echo "❌ Erreur: JAR non généré"
    exit 1
fi

echo "✅ Build terminé"

# ============================================
# 7. CRÉER SERVICE SYSTEMD
# ============================================
echo "🔧 7/8 Création du service systemd..."

# Toujours recréer le service (pour mettre à jour les chemins)
cat > /etc/systemd/system/io.law.service <<EOF
[Unit]
Description=io.law - Consolidation Lois du Bénin
After=mysql.service ollama.service
Wants=mysql.service ollama.service

[Service]
Type=simple
User=${SUDO_USER:-$USER}
WorkingDirectory=$INSTALL_DIR
Environment="JAVA_OPTS=-Xmx4g -Xms1g"
ExecStart=$INSTALL_DIR/scripts/orchestrate.sh
Restart=always
RestartSec=10
StandardOutput=append:$LOGS_DIR/io.law.log
StandardError=append:$LOGS_DIR/io.law-error.log

[Install]
WantedBy=multi-user.target
EOF

# Recharger systemd
systemctl daemon-reload

echo "✅ Service systemd créé"

# ============================================
# 8. SCRIPTS DE GESTION
# ============================================
echo "📝 8/8 Création des scripts de gestion..."

# Rendre orchestrate.sh exécutable
chmod +x "$INSTALL_DIR/scripts/orchestrate.sh"

# Script start - utilise orchestrate.sh (toujours recréé)
cat > /usr/local/bin/io.law-start <<EOFSCRIPT
#!/bin/bash
cd $INSTALL_DIR
./scripts/orchestrate.sh
EOFSCRIPT
chmod +x /usr/local/bin/io.law-start

# Script stop (toujours recréé)
cat > /usr/local/bin/io.law-stop <<'EOFSCRIPT'
#!/bin/bash
echo "⏹️  Arrêt io.law..."
systemctl stop io.law
EOFSCRIPT
chmod +x /usr/local/bin/io.law-stop

# Script status (toujours recréé)
cat > /usr/local/bin/io.law-status <<'EOFSCRIPT'
#!/bin/bash
systemctl status io.law --no-pager
EOFSCRIPT
chmod +x /usr/local/bin/io.law-status

# Script logs (toujours recréé)
cat > /usr/local/bin/io.law-logs <<EOFSCRIPT
#!/bin/bash
tail -f $LOGS_DIR/io.law.log
EOFSCRIPT
chmod +x /usr/local/bin/io.law-logs

# Script update (toujours recréé)
cat > /usr/local/bin/io.law-update <<EOFSCRIPT
#!/bin/bash
set -e
echo "🔄 Mise à jour io.law..."
systemctl stop io.law 2>/dev/null || true
cd $INSTALL_DIR
git pull origin main
mvn clean install -DskipTests -q
systemctl start io.law 2>/dev/null || true
echo "✅ Mise à jour terminée"
EOFSCRIPT
chmod +x /usr/local/bin/io.law-update

echo "✅ Scripts de gestion créés"

# ============================================
# 10. INFORMATIONS FINALES
# ============================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ INSTALLATION TERMINÉE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 INFORMATIONS IMPORTANTES:"
echo ""
echo "🔑 MySQL Root Password: $MYSQL_ROOT_PASSWORD"
echo "   (Sauvegardez ce mot de passe !)"
echo ""
echo "📁 Répertoires:"
echo "   - Installation: $INSTALL_DIR"
echo "   - Données: $DATA_DIR"
echo "   - Logs: $LOGS_DIR"
echo ""
echo "🎮 COMMANDES DISPONIBLES:"
echo ""
echo "   io.law-start    # Démarrer l'orchestration"
echo "   io.law-stop     # Arrêter l'orchestration"
echo "   io.law-status   # Voir le statut"
echo "   io.law-logs     # Suivre les logs en temps réel"
echo "   io.law-update   # Mettre à jour depuis GitHub"
echo ""
echo "🚀 DÉMARRAGE AUTO:"
echo ""
echo "   # Activer démarrage automatique au boot:"
echo "   systemctl enable io.law"
echo ""
echo "   # Démarrer maintenant:"
echo "   io.law-start"
echo ""
echo "📊 MONITORING:"
echo ""
echo "   # Vérifier MySQL:"
echo "   mysql -u root -p law_db  # Password: $MYSQL_ROOT_PASSWORD"
echo ""
echo "   # Vérifier Ollama:"
echo "   curl http://localhost:11434/api/tags"
echo ""
echo "   # Voir fichiers traités:"
echo "   ls -lh $DATA_DIR/pdfs/"
echo "   ls -lh $DATA_DIR/articles/"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💡 PROCHAINES ÉTAPES:"
echo ""
echo "   1. Sauvegarder le mot de passe MySQL"
echo "   2. Démarrer l'application: io.law-start"
echo "   3. Suivre les logs: io.law-logs"
echo ""
echo "✨ Terminé !"
else
    git clone https://github.com/akimsoule/io.law.git
    echo "✅ Projet cloné dans $(pwd)/io.law"
fi

# Se positionner dans le projet
cd io.law
PROJECT_PATH=$(pwd)

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Installation et setup terminés !"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📂 Projet installé dans: $PROJECT_PATH"
echo ""
echo "⚠️  IMPORTANT: Déconnectez-vous et reconnectez-vous pour que"
echo "   le groupe 'docker' soit pris en compte (éviter sudo):"
echo ""
echo "   exit"
echo "   ssh root@your-server-ip"
echo "   cd io.law/scripts_deploy"
echo "   ./deploy.sh"
echo ""

